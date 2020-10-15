package com.noproject.testserver

import java.time.Instant

import cats.effect.IO
import io.circe.Json
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe.jsonEncoderOf
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.circe.jsonOf


class WebhookRoute(private val ql: QueryLog) extends RhoRoutes[IO] with SwaggerSyntax[IO] {
  implicit val enc: EntityEncoder[IO, List[QueryLogItem]] = jsonEncoderOf[IO, List[QueryLogItem]]
  implicit val dec: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  "Post request" **
  POST / "webhook" |>> { req: Request[IO] =>
    for {
      body <- req.as[Json]
      _     = logger.info(s"Received $body")
      res  <- ql.post(Instant.now, body)
    } yield Ok(res)
  }

  "Get requests" **
  GET / "webhook" |>> { req: Request[IO] =>
    for {
      res <- ql.get
    } yield
      Ok(res)
  }
}
