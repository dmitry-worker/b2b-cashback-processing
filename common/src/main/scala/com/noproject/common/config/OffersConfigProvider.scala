package com.noproject.common.config

import java.time.Clock

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import com.noproject.common.domain.dao.ConfigDAO
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}

@Singleton
class OffersConfigProvider @Inject()(
  val configDAO:  ConfigDAO
, val clock:      Clock
, val globalConf: Config
)(
  implicit val T: Timer[IO]
, implicit val C: ContextShift[IO]
) extends DBConfigProvider[OffersConfig]
    with CachedConfigProvider[OffersConfig]
    with FallbackConfigProvider[OffersConfig] {

  override protected val configPath: String = "offers"

  override protected val decoder: Decoder[OffersConfig] = Decoder[OffersConfig]

  override lazy val fallback: ConfigProvider[OffersConfig] = {
    new DefaultFileConfigProvider[OffersConfig](configPath, globalConf)(decoder)
  }

}