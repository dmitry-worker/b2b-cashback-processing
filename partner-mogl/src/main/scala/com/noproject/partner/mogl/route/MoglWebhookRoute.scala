package com.noproject.partner.mogl.route

import java.time.Instant

import cats.effect.IO
import com.noproject.common.config.ConfigProvider
import com.noproject.common.controller.route.Routing
import com.noproject.partner.mogl.MoglIntegrationService
import com.noproject.partner.mogl.config.MoglConfig
import com.noproject.partner.mogl.model.MoglTxnUpdate
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.parse
import javax.inject.{Inject, Singleton}
import org.http4s.Request
import org.http4s.circe.jsonOf
import org.http4s.util.CaseInsensitiveString
import tsec.common._
import tsec.mac.jca.HMACSHA256

@Singleton
class MoglWebhookRoute @Inject()(moglConfigProvider: ConfigProvider[MoglConfig], mIs: MoglIntegrationService) extends Routing {

  implicit val decoder = jsonOf[IO, MoglTxnUpdate]

  implicit val instantDecoder: Decoder[Instant] = Decoder.instance(jv =>
    jv.as[Long].map { millis => Instant.ofEpochMilli(millis) }
  )

  private def getTxn(treq: String): IO[MoglTxnUpdate] = {
    for {
      ubtl <- IO.delay(parse(treq).flatMap(_.as[MoglTxnUpdate]))
      txn = ubtl match {
        case Left(ex) => throw ex
        case Right(v) => v
      }
    } yield txn
  }

  POST / "webhook" / "mogl" / "v1" |>> { req: Request[IO] =>
    val io = for {
      treq     <- req.bodyAsText.compile.string
      givenStr  = req.headers.get(CaseInsensitiveString("NotifySignature")).get.value
      givenMac  = tsec.mac.MAC[HMACSHA256](givenStr.utf8Bytes)
      cfg      <- moglConfigProvider.getConfig
      key       = HMACSHA256.unsafeBuildKey(cfg.api.secret.utf8Bytes)
      verified <- HMACSHA256.verifyBool[IO](treq.utf8Bytes, givenMac, key)
      mtu      <- getTxn(treq)
      _        <- mIs.submitMoglTxn(mtu)
    } yield {
      if (verified) Ok else {
        logger.warn(s"Could not verify ${treq}\n MAC: ${givenMac}")
        BadRequest("Not verified")
      }
    }
  }

}
