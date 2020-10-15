package com.noproject.partner.mogl.config

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.config._
import com.noproject.common.domain.dao.ConfigDAO
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}


@Singleton
class MoglConfigProvider @Inject()(
  val configDAO:  ConfigDAO
, val clock:      Clock
, val globalConf: Config)(
  implicit val T: Timer[IO]
, implicit val C: ContextShift[IO]
)
  extends DBConfigProvider[MoglConfig]
    with CachedConfigProvider[MoglConfig]
    with FallbackConfigProvider[MoglConfig] {

  override protected val configPath: String = "mogl"

  override protected val decoder: Decoder[MoglConfig] = Decoder[MoglConfig]

  override lazy val fallback: ConfigProvider[MoglConfig] = {
    new DefaultFileConfigProvider[MoglConfig](configPath, globalConf)(decoder)
  }

}