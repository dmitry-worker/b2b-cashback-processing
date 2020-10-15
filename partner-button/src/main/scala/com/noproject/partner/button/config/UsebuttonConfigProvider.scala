package com.noproject.partner.button.config

import java.time.Clock

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import com.noproject.common.config._
import com.noproject.common.domain.dao.ConfigDAO
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}

@Singleton
class UsebuttonConfigProvider @Inject()(
  val configDAO:  ConfigDAO
, val clock:      Clock
, val globalConf: Config
)(
  implicit val T: Timer[IO]
, implicit val C: ContextShift[IO]
)
  extends DBConfigProvider[UsebuttonConfig]
    with CachedConfigProvider[UsebuttonConfig]
    with FallbackConfigProvider[UsebuttonConfig] {

  override protected val configPath: String = "usebutton"

  override protected val decoder: Decoder[UsebuttonConfig] = Decoder[UsebuttonConfig]

  override lazy val fallback: ConfigProvider[UsebuttonConfig] = {
    new DefaultFileConfigProvider[UsebuttonConfig](configPath, globalConf)(decoder)
  }

}