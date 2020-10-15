package com.noproject.partner.azigo

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect._
import cats.effect.concurrent.MVar
import cats.implicits._
import cats.instances.queue
import com.noproject.common.Executors
import com.noproject.common.config.ConfigProvider
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.model.customer.{Consumer, WrappedTrackingParams}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.service.{MerchantDataService, MerchantMappingDataService}
import com.noproject.service.PartnerTrackingService
import com.noproject.common.stream.{GroupingQueueResource, RabbitProducer, StreamData, StreamEvent}
import com.noproject.domain.model.merchant.azigo.MerchantAzigo
import com.noproject.partner.azigo.config.AzigoConfig
import com.noproject.partner.azigo.domain.model.AzigoTxn
import io.circe.Json
import io.circe.generic.auto._
import io.prometheus.client.CollectorRegistry
import javax.inject.{Inject, Singleton}
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.io._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{EntityDecoder, Method, Query, Status, Uri}
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.dao.UnknownTransactionDAO
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.service.CommonIntegrationService
import fs2.concurrent.{SignallingRef, Topic}

import scala.concurrent.duration._
import fs2.{Pipe, Stream}

import scala.util.{Failure, Success, Try}


@Singleton
class AzigoIntegrationService @Inject()(
  utDAO:       UnknownTransactionDAO
, mds:         MerchantDataService
, ts:          PartnerTrackingService
, mappingDS:   MerchantMappingDataService
, clock:       java.time.Clock
, azigoCP:     ConfigProvider[AzigoConfig]
, cr:          CollectorRegistry
, txnProducer: RabbitProducer[CashbackTransaction]
, client:      Client[IO]
, txnTopic:    Topic[IO, StreamEvent[AzigoTxn]]
, levelDB:     LevelDBPersistence[CashbackTransaction]
)(implicit
  val cs: ContextShift[IO]
, timer:  Timer[IO]
) extends CommonIntegrationService("azigo", utDAO, txnProducer, mds, ts, cr, clock, levelDB)
  with DefaultLogging {

  def runForever:IO[Unit] = {
    val pipe: Pipe[IO, AzigoTxn, Unit] = input => {
      input.groupWithin(100, 1 seconds).evalMap { chunk =>
        submitAzigoTxns(chunk.toList)
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

  private def submitAzigoTxns(src: List[AzigoTxn]): IO[Unit] = {
    val now = clock.instant
    for {
      mms  <- mappingDS.getMappings
      txns <- transformPrototypes(now, mms, src)
      res  <- submitTransactions(txns)
    } yield res
  }

  private[azigo] def getAzigoTxns(from: Instant, to: Instant): IO[List[AzigoTxn]] = {
    implicit val decoder: EntityDecoder[IO, List[Json]] = jsonOf[IO, List[Json]]

    def query(cfg: AzigoConfig) = Query(
      "key"     -> Some(cfg.affiliate.secret)
    , "from"    -> Some(from.toEpochMilli.toString)
    , "to"      -> Some(to.toEpochMilli.toString)
    )

    def fetch0(uri:Uri): IO[List[AzigoTxn]] = {
      client.fetch(Method.GET(uri)) { resp =>
        lazy val error = new RuntimeException(s"Failed to fetch azigo txns from\n  $resp\n  ${resp.bodyAsText.compile.string.unsafeRunSync()}")
        resp.status match {
          /**
            TODO: Sometimes azigo returns 500 status.
            In this case we should try again later.
          */
          case Status.Conflict | Status.InternalServerError =>
            IO.pure(Nil)
          case Status.Found =>
            resp.headers.get(CaseInsensitiveString("location")) match {
              case Some(loc) => fetch0(Uri.unsafeFromString(loc.value))
              case _ => IO.raiseError(error)
            }
          case Status.Ok =>
            resp.as[List[Json]].map { objects =>
              objects.flatMap { obj =>
                obj.as[AzigoTxn] match {
                  case Right(res) =>
                    Some(res.copy(rawJson = Some(obj)))
                  case Left(error) =>
                    logger.error(s"Failed to parse ${obj} as AzigoTxn: ${error}. Caused by ${error.getCause}")
                    None
                }
              }
            }
          case x =>
            IO.raiseError(error)
        }
      }
    }

    for {
      cfg <- azigoCP.getConfig
      url = Uri.unsafeFromString(cfg.affiliate.api).withPath("/transactions")
      res <- fetch0(url.copy(query = query(cfg)))
    } yield res

  }

  private[azigo] def update0(config: AzigoConfig, mercs: List[MerchantAzigo], mms: MerchantMappings, atTime: Instant): IO[Unit] = {
    val stream = fs2.Stream.emits[IO, MerchantAzigo](mercs).chunkN(50).mapAsyncUnordered(2) {
      azigoMercs =>
        val now = clock.instant()
        val filtered = azigoMercs.filter(!_.storeGuid.isEmpty)
        val merchants = filtered.map(_.asMerchant(mms)).toList
        val offers = filtered.map(_.asOffer(mms, atTime)).toList
        if (offers.isEmpty) IO.unit else {
          for {
            _ <- updateMerchantRows(merchants)
            _ <- updateOfferRows(offers, now)
            q <- mds.markOffersUpdated( offers.map(_.offerId), atTime )
          } yield logger.info(s"Marked a portion of ${q} offers as updated")
        }
    }
    stream.compile.drain.flatMap { _ =>
      logger.warn("Finished azigo synchronization, prepare to delete obsolete offers and update metrics")
      deleteOffers0( atTime ) *> updateMetrics0( atTime )
    }
  }


  private[azigo] def syncAzigoOffers: IO[Unit] = {
    implicit val decoder: EntityDecoder[IO, List[MerchantAzigo]] = jsonOf[IO, List[MerchantAzigo]]
    for {
      cfg <- azigoCP.getConfig
      url = Uri.unsafeFromString(cfg.affiliate.api).withPath("/offers")
      qry = Query("key" -> Some(cfg.affiliate.secret))
      res <- client.expect[List[MerchantAzigo]](url.copy(query = qry))

      now <- IO( clock.instant )
      mms <- mappingDS.getMappings
      upd <- update0( cfg, res, mms, now )
    } yield upd
  }

}
