package com.noproject.partner.coupilia.config

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.config._
import com.noproject.common.domain.dao.ConfigDAO
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}

@Singleton
class CoupiliaConfigProvider @Inject()(
  val configDAO:  ConfigDAO
, val clock:      Clock
, val globalConf: Config
)(
  implicit val T: Timer[IO]
, implicit val C: ContextShift[IO]
)
  extends DBConfigProvider[CoupiliaConfig]
    with CachedConfigProvider[CoupiliaConfig]
    with FallbackConfigProvider[CoupiliaConfig] {

  override protected val configPath: String = "coupilia"

  override protected val decoder: Decoder[CoupiliaConfig] = Decoder[CoupiliaConfig]

  override lazy val fallback: ConfigProvider[CoupiliaConfig] = {
    new DefaultFileConfigProvider[CoupiliaConfig](configPath, globalConf)(decoder)
  }

}
