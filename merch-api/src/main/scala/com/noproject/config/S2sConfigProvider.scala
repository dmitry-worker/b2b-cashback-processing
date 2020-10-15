package com.noproject.config

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.config._
import com.noproject.common.domain.dao.ConfigDAO
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}

@Singleton
class S2sConfigProvider @Inject()(
  val configDAO:  ConfigDAO
, val clock:      Clock
, val globalConf: Config
)(
 implicit val T: Timer[IO]
, implicit val C: ContextShift[IO]
)
  extends DBConfigProvider[S2sConfig]
    with CachedConfigProvider[S2sConfig]
    with FallbackConfigProvider[S2sConfig] {

  override protected val configPath: String = "s2s"

  override protected val decoder: Decoder[S2sConfig] = Decoder[S2sConfig]

  override lazy val fallback: ConfigProvider[S2sConfig] = {
    new DefaultFileConfigProvider[S2sConfig](configPath, globalConf)(decoder)
  }

}