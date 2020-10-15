package com.noproject.partner.button.route

import cats.effect.IO
import com.noproject.common.config.ConfigProvider
import com.noproject.common.controller.route.Routing
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.security.Hash.hmacsha256withSecret
import com.noproject.common.stream.{StreamData, StreamEvent}
import com.noproject.partner.button.UseButtonIntegrationService
import com.noproject.partner.button.config.UsebuttonConfig
import com.noproject.partner.button.domain.model.{UsebuttonCodecs, UsebuttonPayload, UsebuttonTxn}
import fs2.concurrent.Topic
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser.parse
import javax.inject.{Inject, Singleton}
import org.http4s.circe.jsonOf
import org.http4s.util.CaseInsensitiveString
import org.http4s.{EntityDecoder, Request}

@Singleton
class UsebuttonWebhookRoute @Inject()(
  ubConfig: ConfigProvider[UsebuttonConfig]
, txnTopic: Topic[IO, StreamEvent[UsebuttonPayload]]
, levelDB:  LevelDBPersistence[CashbackTransaction]
) extends Routing
  with UsebuttonCodecs
{

  sealed case class SignVerificationException() extends Throwable

  private val XBUTTON_AUTH_HEADER = "X-Button-Signature"

  implicit def txnDecoder: EntityDecoder[IO, UsebuttonTxn] = jsonOf[IO, UsebuttonTxn]
  implicit def jDecoder: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  private def verifyRequest(authHeader: String, treq: String): IO[Boolean] = {
    for {
      conf     <- ubConfig.getConfig
      newMac    = hmacsha256withSecret.hex(conf.webhookSecret, treq)
      _         = logger.info(s"Verifying usebutton sign. Auth header is ${authHeader}, calculated mac is ${newMac}")
    } yield newMac.contains(authHeader)
  }


  private def handleTxn(treq: String): IO[UsebuttonPayload] = {
    for {
      json <- IO.delay(parse(treq).right.get)
      ubtl <- IO.delay(json.as[UsebuttonTxn])
      txn   = ubtl match {
                case Left(ex) => throw ex
                case Right(v) => v.data.copy(rawJson  = Some(json))
              }
      _    <- txnTopic.publish1(StreamData(txn))//ubIs.submitUsebuttonTxn(txn)
    } yield txn
  }

  POST / "webhook" / "v1" / "usebutton" |>> { req: Request[IO] =>
    val authHeaderOpt = req.headers.get(CaseInsensitiveString(XBUTTON_AUTH_HEADER))

    authHeaderOpt match {
      case None             => Unauthorized("")
      case Some(authHeader) =>
        val io = for {
          treq     <- req.bodyAsText.compile.string
          _         = logger.info(s"Raw request body is $treq")
          verified <- verifyRequest(authHeader.value, treq)
          res      <- if (verified) handleTxn(treq)
                      else IO.raiseError(SignVerificationException())
        } yield res

        io.redeemWith ({
          case ex: SignVerificationException =>
            logger.warn(s"Usebutton request $req not verified")
            Unauthorized("")
          case ex =>
            logger.error(s"Incorrect usebutton txn: ${ex}")
          InternalServerError("")
        }, { txn =>
          logger.info(s"Processing usebutton txn ${txn}")
          Ok("")
        })
    }
  }
}
