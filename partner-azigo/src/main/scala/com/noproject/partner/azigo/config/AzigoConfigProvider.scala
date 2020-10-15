package com.noproject.partner.azigo.config

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.config.{CachedConfigProvider, ConfigProvider, DBConfigProvider, DefaultFileConfigProvider, FallbackConfigProvider, FileConfigProvider}
import com.noproject.common.domain.dao.ConfigDAO
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}

@Singleton
class AzigoConfigProvider @Inject()(
  val configDAO:  ConfigDAO
, val clock:      Clock
, val globalConf: Config
)(
  implicit val T: Timer[IO]
, implicit val C: ContextShift[IO]
)
  extends DBConfigProvider[AzigoConfig]
    with CachedConfigProvider[AzigoConfig]
    with FallbackConfigProvider[AzigoConfig] {

  override protected val configPath: String = "azigo"

  override protected val decoder: Decoder[AzigoConfig] = Decoder[AzigoConfig]

  override lazy val fallback: ConfigProvider[AzigoConfig] = {
    new DefaultFileConfigProvider[AzigoConfig](configPath, globalConf)(decoder)
  }

}