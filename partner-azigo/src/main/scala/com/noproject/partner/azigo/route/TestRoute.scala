package com.noproject.partner.azigo.route

import cats.effect.IO
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.dto.CommonError
import com.noproject.common.controller.route.Routing
import com.noproject.common.stream.{StreamData, StreamEvent}
import com.noproject.partner.azigo.domain.model.AzigoTxn
import fs2.concurrent.Topic
import io.circe.Json
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import org.http4s.{EntityDecoder, Request}
import org.http4s.circe.jsonOf

@Singleton
class TestRoute @Inject()(
  env:      EnvironmentMode
, txnTopic: Topic[IO, StreamEvent[AzigoTxn]]
) extends Routing {

  implicit val dec: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  "Post test azigo txn to worker (not available at Prod env)" **
  POST / "crap" / "txn" |>> { req: Request[IO] =>
    if (env == EnvironmentMode.Prod) {
      Unauthorized(CommonError(message = "Not available at prod"))
    } else {
      val io = for {
        json  <-  req.as[Json]
        _     <-  json.as[AzigoTxn] match {
          case Right(ctxn) =>
            txnTopic.publish1(StreamData(ctxn))
          case Left(err) =>
            logger.warn(s"Failed to parse azigo txn from ${json} because of ${err}")
            IO.raiseError(new IllegalArgumentException("Invalid json format"))
        }
      } yield ()
      redeemAndSuccess(io)
    }
  }

}
