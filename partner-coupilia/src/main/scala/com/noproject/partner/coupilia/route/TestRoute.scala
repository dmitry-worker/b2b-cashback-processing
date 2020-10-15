package com.noproject.partner.coupilia.route

import cats.effect.IO
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.dto.CommonError
import com.noproject.common.controller.route.Routing
import com.noproject.common.stream.{StreamData, StreamEvent}
import com.noproject.partner.coupilia.domain.model.{CoupiliaCodec, CoupiliaTxn}
import com.noproject.partner.coupilia.service.CoupiliaIntegrationService
import fs2.concurrent.Topic
import io.circe.Json
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import org.http4s.{DecodeFailure, DecodeResult, EntityDecoder, InvalidMessageBodyFailure, Request}
import org.http4s.circe.jsonOf

import scala.util.Success

@Singleton
class TestRoute @Inject()(
  env: EnvironmentMode
, topic: Topic[IO, StreamEvent[CoupiliaTxn]]
) extends Routing {

  import CoupiliaCodec._

  implicit val dec: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  "Post test coupilia txn to worker (not available at Prod env)" **
  POST / "crap" / "txn" |>> { req: Request[IO] =>
    if (env == EnvironmentMode.Prod) {
      Unauthorized(CommonError(message = "Not available at prod"))
    } else {
      val io = for {
        json    <-  req.as[Json]
        norm    <-  IO.delay(normalize(json))
        result  <-  norm.as[CoupiliaTxn] match {
                      case Right(ctxn) =>
                        topic.publish1(StreamData(ctxn))
                      case Left(err) =>
                        logger.warn(s"Failed to parse coupilia txn from ${json} because of ${err}")
                        IO.raiseError(new IllegalArgumentException("Invalid json format"))
                    }
      } yield ()
      redeemAndSuccess(io)
    }
  }

}
