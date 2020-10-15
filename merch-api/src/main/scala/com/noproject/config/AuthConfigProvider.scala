package com.noproject.config

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.config.{CachedConfigProvider, ConfigProvider, DBConfigProvider, DefaultFileConfigProvider, FallbackConfigProvider}
import com.noproject.common.domain.dao.ConfigDAO
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}

@Singleton
class AuthConfigProvider @Inject()(
  val configDAO:  ConfigDAO
, val clock:      Clock
, val globalConf: Config
)(
  implicit val T: Timer[IO]
, implicit val C: ContextShift[IO]
) extends DBConfigProvider[AuthConfig]
  with CachedConfigProvider[AuthConfig]
  with FallbackConfigProvider[AuthConfig] {

  override protected val configPath: String = "auth"

  override protected val decoder: Decoder[AuthConfig] = Decoder[AuthConfig]

  override lazy val fallback: ConfigProvider[AuthConfig] = {
    new DefaultFileConfigProvider[AuthConfig](configPath, globalConf)(decoder)
  }

}