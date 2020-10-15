package com.noproject.partner.mogl.route

import cats.effect.IO
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.dto.CommonError
import com.noproject.common.controller.route.Routing
import com.noproject.partner.mogl.MoglIntegrationService
import com.noproject.partner.mogl.model.MoglTxn
import io.circe.Json
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import org.http4s.{DecodeResult, EntityDecoder, Request}
import org.http4s.circe.jsonOf

@Singleton
class TestRoute @Inject()(env: EnvironmentMode, service: MoglIntegrationService) extends Routing {

  implicit val dec: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  "Post test mogl txn to worker (not available at Prod env)" **
  POST / "crap" / "txn" |>> { req: Request[IO] =>
    if (env == EnvironmentMode.Prod) {
      Unauthorized(CommonError(message = "Not available at prod"))
    } else {
      // TODO transaction processing not implemented
      Ok("")
    }

  }
}
