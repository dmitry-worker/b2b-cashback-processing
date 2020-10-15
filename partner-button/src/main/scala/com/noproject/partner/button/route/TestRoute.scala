package com.noproject.partner.button.route

import cats.effect.IO
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.dto.CommonError
import com.noproject.common.controller.route.Routing
import com.noproject.common.stream.{StreamData, StreamEvent}
import com.noproject.partner.button.UseButtonIntegrationService
import com.noproject.partner.button.domain.model.{UsebuttonCents, UsebuttonCodecs, UsebuttonPayload, UsebuttonTxn}
import fs2.concurrent.Topic
import io.circe.{Decoder, Json}
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import org.http4s.circe.jsonOf
import org.http4s.{DecodeResult, EntityDecoder, Request}

@Singleton
class TestRoute @Inject()(
  env: EnvironmentMode
, service: UseButtonIntegrationService
, txnTopic: Topic[IO, StreamEvent[UsebuttonPayload]]
) extends Routing with UsebuttonCodecs {

  implicit val dec: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  "Post test button txn to worker (not available at Prod env)" **
  POST / "crap" / "txn" |>> { req: Request[IO] =>
    if (env == EnvironmentMode.Prod) {
      Unauthorized(CommonError(message = "Not available at prod"))
    } else {
      val io = for {
        json    <-  req.as[Json]
        result  <-  json.as[UsebuttonTxn] match {
          case Right(ctxn) =>
            txnTopic.publish1(StreamData(ctxn.data))
          case Left(err) =>
            logger.warn(s"Failed to parse usebutton txn from ${json} because of ${err}")
            IO.raiseError(new IllegalArgumentException("Invalid json format"))
        }
      } yield ()
      redeemAndSuccess(io)
    }
  }
}
