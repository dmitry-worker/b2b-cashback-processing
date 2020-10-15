package com.noproject.processor.route

import cats.effect.IO
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.dto.CommonError
import com.noproject.common.controller.route.Routing
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.stream.RabbitProducer
import io.circe.Json
import javax.inject.{Inject, Singleton}
import org.http4s.{EntityDecoder, Request}
import org.http4s.circe.jsonOf

@Singleton
class TestRoute @Inject()(env: EnvironmentMode, tp: RabbitProducer[CashbackTransaction]) extends Routing {

  import com.noproject.common.domain.codec.DomainCodecs._

  implicit val dec: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  "Post test transaction to rabbit (not available at Prod env)" **
  POST / "crap" / "txn" |>> { req: Request[IO] =>
    if (env == EnvironmentMode.Prod) {
      Unauthorized(CommonError(message = "Not available at prod"))
    } else {
      val io = for {
        json    <-  req.as[Json]
        result  <-  json.as[CashbackTransaction] match {
          case Right(ctxn) =>
            tp.submit(ctxn.customerName, List(ctxn))
          case Left(err) =>
            logger.warn(s"Failed to parse usebutton txn from ${json} because of ${err}")
            IO.raiseError(new IllegalArgumentException("Invalid json format"))
        }
      } yield ()
      redeemAndSuccess(io)
    }
  }

  "Post list of test transactions to rabbit (not available at Prod env)" **
    POST / "crap" / "txns" |>> { req: Request[IO] =>
    if (env == EnvironmentMode.Prod) {
      Unauthorized(CommonError(message = "Not available at prod"))
    } else {
      val io = for {
        json    <-  req.as[Json]
        result  <-  json.as[List[CashbackTransaction]] match {
          case Right(ctxn) =>
            tp.submit(ctxn.head.customerName, ctxn)
          case Left(err) =>
            logger.warn(s"Failed to parse usebutton txn from ${json} because of ${err}")
            IO.raiseError(new IllegalArgumentException("Invalid json format"))
        }
      } yield ()
      redeemAndSuccess(io)
    }
  }
}
 