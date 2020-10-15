package com.noproject.common.controller.route

import cats.effect.IO
import cats.implicits._
import com.noproject.common.config.ConfigProvider
import com.noproject.common.controller.dto.HealthCheckResponse
import io.circe.generic.auto._
import javax.inject.{Inject, Named, Singleton}
import org.http4s.circe.jsonEncoderOf
import org.http4s.{EntityEncoder, Request}

@Singleton
class HealthCheckRoute @Inject()(
  @Named("appVersion") version: String
) extends Routing {

  implicit val dtoEncoder: EntityEncoder[IO, HealthCheckResponse] = jsonEncoderOf[IO, HealthCheckResponse]

  "Health check" **
  GET / baseApiPath / "health-check" |>> { _: Request[IO] =>
    logger.info("Health-check")
    Ok(HealthCheckResponse(version = version))
  }

}
