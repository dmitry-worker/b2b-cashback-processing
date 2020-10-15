package com.noproject.partner.button

import java.time.{Clock, Instant}

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.noproject.common.codec.json.InstantCodecs
import com.noproject.common.config.ConfigProvider
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.stream.RabbitProducer
import com.noproject.partner.button.config.UsebuttonConfig
import com.noproject.partner.button.domain.model._
import com.noproject.service.PartnerTrackingService
import io.circe.Json
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import org.http4s._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import scala.concurrent.duration._

@Singleton
class UseButtonUpdaterService @Inject()(
  buttonCP:    ConfigProvider[UsebuttonConfig]
, ts:          PartnerTrackingService
, service:     UseButtonIntegrationService
, eventProd:   RabbitProducer[EventLogItem]
, client:      Client[IO]
, clock:       Clock
)(implicit cs: ContextShift[IO], timer: Timer[IO]) extends DefaultLogging
  with InstantCodecs
  with UsebuttonCodecs {

  private lazy val busy: Ref[IO, Boolean] = Ref.of[IO, Boolean](false).unsafeRunSync()

  implicit val txnUpdateDecoder: EntityDecoder[IO, UsebuttonTxnUpdate] = jsonOf[IO, UsebuttonTxnUpdate]

  protected[button] def fetchUpdateChunk(uri: Uri, cfg: UsebuttonConfig): IO[(List[UsebuttonPayload], List[EventLogItem], Option[String])] = {
    implicit val decoder: EntityDecoder[IO, Json] = jsonOf[IO, Json]
    val request: Request[IO] = Request[IO](
      method = GET
      , uri = uri
      , headers = Headers.of(Authorization(BasicCredentials(cfg.apiKey, cfg.apiSecret)))
    )

    def transformObjects(objects: List[Json]): (List[EventLogItem], List[UsebuttonPayload]) = {
      import com.noproject.common.domain.model.eventlog.JsonEventLogItem.JsonEventLogItem

      val now = clock.instant
      val txnsIO = objects.map { json: Json =>
          json.as[UsebuttonPayload].bimap ({ left =>
            json.asEventLogItem(left.message)
          }, { obj =>
            obj.copy(rawJson = Some(json))
          })
      }

      val (events, txns) = txnsIO.separate
      (events, txns)
    }

    client.fetch(request) { resp: Response[IO] =>
      lazy val error = new RuntimeException(s"Failed to fetch usebutton txns from\n $request\n ${resp.bodyAsText.compile.string.unsafeRunSync()}")
      resp.status match {
        case Status.Ok => for {
          json  <- resp.as[Json]
          _      = logger.info(s"Fetched usebutton txn update $json")
          // that's all for get rawJson for each txn from `objects` json array
          meta  <- IO.delay(json.hcursor.downField("meta").as[UsebuttonMetaUpdate].right.get)
          objs  <- IO.delay(json.hcursor.downField("objects").as[List[Json]].right.get)
          (events, txns) = transformObjects(objs)
        } yield (txns, events, meta.next)

        case _ => IO.raiseError(error)
      }
    }
  }

  private def fetchFirstUpdateChunk(from: Instant, to: Instant, cfg: UsebuttonConfig): IO[(List[UsebuttonPayload], List[EventLogItem], Option[String])] = {
    val headers = Headers.of(Authorization(BasicCredentials(cfg.apiKey, cfg.apiSecret)))

    val uri = Uri
      .unsafeFromString(cfg.url)
      .withPath(s"/v1/affiliation/accounts/${cfg.accountId}/transactions")
      .withQueryParam("start", formatDate(from))
      .withQueryParam("end", formatDate(to))
    fetchUpdateChunk(uri, cfg)
  }

  private def tryToFetchNextUpdateChunk(uri: Option[String], cfg: UsebuttonConfig): IO[(List[UsebuttonPayload], List[EventLogItem], Option[String])] = {
    if (uri.isEmpty) IO.pure((List.empty, List.empty, None))
    else fetchUpdateChunk(Uri.unsafeFromString(uri.get), cfg)
  }
    
  private def sendErrors(ev: List[EventLogItem]): IO[Unit] = {
    eventProd.submit(ev.head.objectType.entryName, ev)
  }

  def getAndUpdateUsebuttonTxns(from: Instant, to: Instant): IO[Unit] = {

    def streamingUpdate(cfg: UsebuttonConfig): IO[Unit] = {
      fs2.Stream
        .unfoldEval(fetchFirstUpdateChunk(from, to, cfg)) { tuio =>
          tuio.flatMap { case (tu, ev, next) =>
            for {
              _ <- service.submitUsebuttonTxns(tu)
              _ <- if (ev.nonEmpty) sendErrors(ev) else IO.unit
            } yield {
              if (tu.isEmpty) None
              else Some(((tu, ev), tryToFetchNextUpdateChunk(next, cfg)))
            }
          }
        }
        .compile
        .drain

    }

    val updaterIO = busy.get.flatMap {
      case true => IO.unit
      case false =>
        for {
          _   <- busy.set(true)
          _    = logger.info("Usebutton updater started")
          cfg <- buttonCP.getConfig
          res <- streamingUpdate(cfg)
        } yield res
    }

    updaterIO.redeemWith ({ ex: Throwable =>
      logger.error(ex)("Usebutton updater failure")
      busy.set(false)
    }, { _ =>
      logger.info("Usebutton updater finished")
      busy.set(false)
    })

  }

  def isBusy: IO[Boolean] = busy.get
}
