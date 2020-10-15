package com.noproject.partner.coupilia.service

import java.time._
import java.util.concurrent.atomic.AtomicBoolean

import cats.effect.{Concurrent, ContextShift, IO, Resource, Timer}
import com.noproject.common.Executors
import com.noproject.common.config.{ConfigProvider, EnvironmentMode}
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.{MerchantDataService, MerchantMappingDataService}
import com.noproject.service.PartnerTrackingService
import com.noproject.common.stream.{RabbitProducer, StreamData, StreamEvent}
import com.noproject.partner.coupilia.config.CoupiliaConfig
import com.noproject.partner.coupilia.domain.model.{CoupiliaCodec, CoupiliaTxn, MerchantCoupilia, OfferCoupilia}
import io.circe._
import io.circe.generic.auto._
import io.prometheus.client.CollectorRegistry
import javax.inject.{Inject, Singleton}
import cats.implicits._
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, MerchantRow}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import com.noproject.common.logging.DefaultLogging
import fs2.Pipe

import scala.concurrent.duration._
import cats.effect.{ContextShift, IO}
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.dao.UnknownTransactionDAO
import com.noproject.common.service.CommonIntegrationService
import com.noproject.partner.coupilia.Application.envMode
import fs2.concurrent.Topic
import org.http4s.{EntityDecoder, Uri}

@Singleton
class CoupiliaIntegrationService @Inject()(
  utDAO:        UnknownTransactionDAO
, mds:          MerchantDataService
, mmds:         MerchantMappingDataService
, coupiliaCP:   ConfigProvider[CoupiliaConfig]
, cr:           CollectorRegistry
, ts:           PartnerTrackingService
, http:         Client[IO]
, clock:        Clock
//, envMode:      EnvironmentMode
, txnProducer:  RabbitProducer[CashbackTransaction]
, txnTopic:     Topic[IO, StreamEvent[CoupiliaTxn]]
, levelDB:      LevelDBPersistence[CashbackTransaction]
)( implicit val cs: ContextShift[IO], timer: Timer[IO], conc: Concurrent[IO])
    extends CommonIntegrationService("coupilia", utDAO, txnProducer, mds, ts, cr, clock, levelDB)(timer, conc)
    with DefaultLogging {

  val BATCH_SIZE: Int       = 50

//  TODO: enable this when coupilia is on
//  val OFFER_SYNC_DELAY: Int = if (envMode == EnvironmentMode.Test) 1 else 10
//  val TXN_SYNC_DELAY: Int   = if (envMode == EnvironmentMode.Test) 2 else 60

  def runForever:IO[Unit] = {
    val pipe: Pipe[IO, CoupiliaTxn, Unit] = input => {
      input.groupWithin(100, 1 seconds).evalMap { chunk =>
        submitCoupiliaTxns(chunk.toList)
      }
    }

    val io1: IO[Unit] = txnTopic
      .subscribe(100)
      .collect { case StreamData(txn) => txn }
      .through(pipe)
      .compile.drain

    val io2: IO[Unit] = fs2.Stream
      .awakeDelay[IO](1 seconds)
      .evalMap { _ => leveldb2rabbit }
      .compile.drain

    //    TODO: enable this when coupilia is on
    //    val io3 = fs2.Stream.fixedDelay(TXN_SYNC_DELAY minutes)
    //      .evalMap(_ => service.syncCoupiliaTxns(fetchFrom, fetchTo) )
    //      .compile.drain
    //
    //    val io4 = fs2.Stream.fixedDelay(OFFER_SYNC_DELAY minutes)
    //      .evalMap(_ => service.syncCoupiliaOffers)
    //      .compile.drain


    List(io1, io2/*, io3, io4*/).parSequence.void
  }


  ////////////////
  // offers
  private[coupilia] def getOffers(cfg: CoupiliaConfig): IO[List[OfferCoupilia]] = {
    implicit val jsonDecoder: EntityDecoder[IO, Json] = jsonOf[IO, Json]
    val request = Uri
      .unsafeFromString(cfg.couponUrl)
      .withQueryParam("token", cfg.secret)
    http.expect[Json](request).map { json =>
      CoupiliaCodec.normalize(json).as[List[OfferCoupilia]] match {
        case Left(err) => throw err
        case Right(ms) => ms
      }
    }
  }

  private[coupilia] def getMerchants(cfg: CoupiliaConfig): IO[List[MerchantCoupilia]] = {
    implicit val jsonDecoder: EntityDecoder[IO, Json] = jsonOf[IO, Json]
    val request = Uri
      .unsafeFromString(cfg.merchantUrl)
      .withQueryParam("token", cfg.secret)
    http.expect[Json](request).map { json =>
      CoupiliaCodec.normalize(json).as[List[MerchantCoupilia]] match {
        case Left(err) => throw err
        case Right(ms) => ms
      }
    }
  }

  private[coupilia] def syncCoupiliaOffers: IO[Unit] = {
    implicit val offerDecoder: EntityDecoder[IO, List[OfferCoupilia]] = jsonOf[IO, List[OfferCoupilia]]
    implicit val groupDecoder: EntityDecoder[IO, List[MerchantCoupilia]] = jsonOf[IO, List[MerchantCoupilia]]
    val now = clock.instant()

    def composeMerchantsAndOffers(
      mercs: List[MerchantCoupilia]
    , offers: List[OfferCoupilia]
    , mappings: MerchantMappings
    ): Map[MerchantRow, List[MerchantOfferRow]] = {

      val gmap  = mercs.map(g => g.name -> g).toMap
      val now   = Instant.now

      val result = offers.groupBy(_.merchant).flatMap {
        case (name, coupOffers) =>
          gmap.get(name).map { g =>
            val merchantRow = g.asMerchantRow(mappings)
            val offerRows = coupOffers.map { co => co.asOffer(g, mappings, now) }
            merchantRow -> offerRows
          }
      }
      result
    }

    val io = for {
      cfg       <- coupiliaCP.getConfig
      cMercs    <- getMerchants(cfg)
      cOffers   <- getOffers(cfg)
      mappings  <- mmds.getMappings
      offerMap  =  composeMerchantsAndOffers(cMercs, cOffers, mappings)
      _         <- updateMerchantRows(offerMap.keys.toList)
      _         <- updateOfferRows(offerMap.values.toList.flatten, now)
    } yield ()

    val result = io.flatMap { _ =>
      logger.warn(s"Finished coupilia synchronization, prepare to delete obsolete offers and update metrics")
      deleteOffers0( now ) *> updateMetrics0( now )
    }
    result
  }

  ////////////////
  // transactions
  def syncCoupiliaTxns(from: LocalDate, to: LocalDate): IO[Unit] = {
    for {
      cfg   <- coupiliaCP.getConfig
      txns  <- getTxns(from, to, cfg)
      res   <- submitCoupiliaTxns(txns)
    } yield res
  }

  private[coupilia] def submitCoupiliaTxns(src: List[CoupiliaTxn]): IO[Unit] = {
    val now = clock.instant
    for {
      mms  <- mmds.getMappings
      txns <- transformPrototypes(now, mms, src)
      res  <- submitTransactions(txns)
    } yield res
  }

  private[coupilia] def getTxns(from: LocalDate, to: LocalDate, config: CoupiliaConfig): IO[List[CoupiliaTxn]] = {
    implicit val jsonListDecoder: EntityDecoder[IO, List[Json]] = jsonOf[IO, List[Json]]

    val fmt = CoupiliaCodec.dateFormat
    val (formattedStartDate, formattedEndDate) = fmt.format(from) -> fmt.format(to)

    val uriWithParams = Uri.unsafeFromString(config.txnUrl)
      .withQueryParam("startDate", formattedStartDate)
      .withQueryParam("endDate", formattedEndDate)
      .withQueryParam("token", config.secret)

    def parseInvalidJsonTransactions(json: List[Json]): IO[List[CoupiliaTxn]] = {
      IO.delay {
        json.map { el =>
          CoupiliaCodec.normalize(el).as[CoupiliaTxn] match {
            case Right(txn) => txn.copy(rawJson = Some(el))
            case Left(err)  => throw err
          }
        }
      }
    }

    for {
      resp    <- http.expect[List[Json]](uriWithParams)
      result  <- parseInvalidJsonTransactions(resp) // resp.entity.withContentType(ContentTypes.`application/json`)
    } yield result

  }

//  private[coupilia] def handleTxnsChunk(src: List[CoupiliaTxn]): IO[Unit] = {
//    def resolve(mms: MerchantMappings, now: Instant, src: List[CoupiliaTxn], wpts: Map[String, Try[WrappedTrackingParams]]): List[CashbackTransaction] = {
//      val (valid, invalid) = src.toList.partition(cTxn => cTxn.subaffiliateid.exists(saf => wpts.get(saf).exists(_.isSuccess)))
//      // TODO: what to do with invalid?
//      valid.map { ct =>
//        val params = wpts(ct.subaffiliateid.get).get
//        ct.asCashbackTransaction(params, now, mms)
//      }
//    }
//
//    for {
//      mappings <- mmds.getMappings
//      now       = Instant.now()
//      wpts     <- ts.decodeHashesBatchAndGetTrackingParams(src.toList.flatMap(_.subaffiliateid))
//      txns      = resolve(mappings, now, src, wpts)
//      _        <- submitTransactions(txns)
//    } yield ()
//  }

}
