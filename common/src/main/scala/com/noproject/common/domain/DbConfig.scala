package com.noproject.common.domain

import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.config.syntax._
import io.circe.generic.auto._

case class DbConfig (
  jdbcUrl:  String
, user:     String
, password: String
, migrationEnabled: Boolean
)

object DbConfig {

  def fromConfig(config: Config): DbConfig = {
    config.as[DbConfig].fold(
      left => throw new RuntimeException(s"Can't parse DbConfig, reason: '${left.getMessage}'"),
      cfg => cfg
    )
  }
}