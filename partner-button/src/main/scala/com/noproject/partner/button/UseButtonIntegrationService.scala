package com.noproject.partner.button

import java.time.Instant

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import com.noproject.common.codec.json.InstantCodecs
import com.noproject.common.config.ConfigProvider
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.model.customer.WrappedTrackingParams
import com.noproject.common.domain.model.merchant.MerchantItem
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.service.{MerchantDataService, MerchantMappingDataService}
import com.noproject.service.PartnerTrackingService
import com.noproject.common.stream.{RabbitProducer, StreamData, StreamEvent}
import com.noproject.partner.button.config.UsebuttonConfig
import com.noproject.partner.button.domain.dao.UsebuttonMerchantDAO
import com.noproject.partner.button.domain.model._
import io.circe.generic.auto._
import io.prometheus.client.CollectorRegistry
import javax.inject.{Inject, Singleton}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s._
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.dao.UnknownTransactionDAO
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.service.CommonIntegrationService
import fs2.Pipe
import fs2.concurrent.Topic

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

@Singleton
class UseButtonIntegrationService @Inject() (
  utDAO:       UnknownTransactionDAO
, mds:         MerchantDataService
, ubmDAO:      UsebuttonMerchantDAO
, ts:          PartnerTrackingService
, mappingDS:   MerchantMappingDataService
, clock:       java.time.Clock
, buttonCP:    ConfigProvider[UsebuttonConfig]
, cr:          CollectorRegistry
, txnProducer: RabbitProducer[CashbackTransaction]
, client:      Client[IO]
, txnTopic:    Topic[IO, StreamEvent[UsebuttonPayload]]
, levelDB:    LevelDBPersistence[CashbackTransaction]
) (implicit val cs: ContextShift[IO], timer: Timer[IO], conc: Concurrent[IO])
  extends CommonIntegrationService("usebutton", utDAO, txnProducer, mds, ts, cr, clock, levelDB)(timer, conc)
    with DefaultLogging
    with InstantCodecs
    with UsebuttonCodecs
{

  private implicit val offerRequestEncoder: EntityEncoder[IO, UsebuttonOfferRequest] = jsonEncoderOf[IO, UsebuttonOfferRequest]
  private implicit val merchantDecoder: EntityDecoder[IO, UsebuttonMerchantsResponse] = jsonOf[IO, UsebuttonMerchantsResponse]
  private implicit val offerDecoder: EntityDecoder[IO, UsebuttonOffersResponse] = jsonOf[IO, UsebuttonOffersResponse]
  private implicit val txnUpdateDecoder: EntityDecoder[IO, UsebuttonTxnUpdate] = jsonOf[IO, UsebuttonTxnUpdate]

  def runForever:IO[Unit] = {
    val pipe: Pipe[IO, UsebuttonPayload, Unit] = input => {
      input.groupWithin(100, 1 seconds).evalMap { chunk =>
        submitUsebuttonTxns(chunk.toList)
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

    List(io1, io2).parSequence.void
  }

  def submitUsebuttonTxns(src: List[UsebuttonPayload]): IO[Unit] = {
    val now = clock.instant

    def fillTxnsWithCustomer(src: List[UsebuttonPayload]): IO[List[CashbackTransaction]] = {

      def resolve(src: List[UsebuttonPayload], hashes: Map[String, Try[WrappedTrackingParams]], mms: MerchantMappings): List[CashbackTransaction] = {
        src.map { txn =>
          val params = txn.publisher_customer_id match {
            case None =>
              logger.warn(s"Empty tracking params for txn ${txn.id}")
              WrappedTrackingParams.empty
            case Some(uid) =>
              hashes(uid) match {
                case Success(wtp) =>
                  wtp
                case Failure(th) =>
                  th.printStackTrace()
                  logger.warn(s"Couldn't recover tracking params for txn ${txn.id}")
                  WrappedTrackingParams.empty
              }
          }
          txn.asCashbackTransaction(params, now, mms)
        }

      }

      for {
        hashes <- ts.decodeHashesBatchAndGetTrackingParams(src.flatMap(_.publisher_customer_id))
        mms    <- mappingDS.getMappings
        result <- IO.delay( resolve(src, hashes, mms) )
      } yield result

    }

    fillTxnsWithCustomer(src) flatMap submitTransactions


//    for {
//      cu  <- user
//      txn  = src.asCashbackTransaction(cu, now)
//      _    = logger.info(s"Usebutton txn is $txn. Tracking params are $cu")
//      res <- submitTransactions(List(txn))
//    } yield res
  }

  private[button] def submitUsebuttonTxnsUpdates(txns: List[CashbackTransaction]): IO[Unit] = {
    submitTransactions(txns)
  }

  private[button] def update0[T<: MerchantItem](config: UsebuttonConfig, mercs: List[T], mms: MerchantMappings, atTime: Instant): IO[Unit] = {
    val stream = fs2.Stream.emits[IO, T](mercs).chunkN(50).mapAsyncUnordered(2) {
      ubMercs =>
        val merchants = ubMercs.map(_.asMerchant(mms)).toList
        val offers = ubMercs.map(_.asOffer(mms, atTime)).toList
        if (offers.isEmpty) IO.unit else {
          for {
            _ <- updateMerchantRows(merchants)
            _ <- updateOfferRows(offers, atTime)
            q <- mds.markOffersUpdated( offers.map( _.offerId ), atTime )
          } yield logger.info(s"Marked a portion of ${q} offers as updated")
        }
    }
    stream.compile.drain.flatMap { _ =>
      logger.warn("Finished usebutton synchronization, prepare to delete obsolete offers and update metrics")
      deleteOffers0(atTime) *> updateMetrics0(atTime)
    }
  }

  private[button] def syncUsebuttonOffersWithRestApi: IO[Unit] = {
    def buildMercsRequest(cfg: UsebuttonConfig): Request[IO] = {
      Request[IO](
        method = GET
      , uri = Uri.unsafeFromString(cfg.url).withPath("/v1/merchants")
      , headers = Headers.of(Authorization(BasicCredentials(cfg.apiKey, cfg.apiSecret)))
      )
    }

    def buildOffersRequest(cfg: UsebuttonConfig): Request[IO] = {
      Request[IO](
        method = POST
        , uri = Uri.unsafeFromString(cfg.url).withPath("/v1/offers")
        , headers = Headers.of(Authorization(BasicCredentials(cfg.apiKey, cfg.apiSecret)))
      ).withEntity(UsebuttonOfferRequest())
    }

    for {
      now <- IO( clock.instant )
      cfg  <- buttonCP.getConfig
      resM <- client.expect[UsebuttonMerchantsResponse](buildMercsRequest(cfg))
      resO <- client.expect[UsebuttonOffersResponse](buildOffersRequest(cfg))
      res  <- IO.delay(merchantPostpocessing(resM.objects, resO.`object`.merchant_offers, cfg))
      mms  <- mappingDS.getMappings
      now   = clock.instant()
      upd  <- update0(cfg, res, mms, now)
    } yield upd
  }

  private def merchantPostpocessing(
    mercs: List[UsebuttonMerchantResponseItem]
  , offers: List[UsebuttonOffersResponseItem]
  , cfg: UsebuttonConfig): List[UsebuttonMerchant] = {
    val ubmercs: List[UsebuttonMerchant] = mercs.flatMap { merc: UsebuttonMerchantResponseItem =>
      offers.filter(_.merchant_id == merc.id).map { offerItem =>
        val ubOffersList = offerItem.offers.map(_.asUsebuttonOffer)
        merc.toMerchant(ubOffersList, offerItem.best_offer_id, cfg.organizationId)
      }
    }
    ubmercs
  }

//  @deprecated("Use syncUsebuttonOffersWithRestApi instead", "2019-10-07")
//  private[button] def syncUsebuttonOffersWithDb: IO[Unit] =
//    for {
//  now <- IO( clock.instant )
//      cfg <- buttonCP.getConfig
//      res <- ubmDAO.findAll
//      mms <- mappingDS.getMappings
//      now = clock.instant()
//      upd <- update0(cfg, res, mms, now)
//    } yield upd
}
