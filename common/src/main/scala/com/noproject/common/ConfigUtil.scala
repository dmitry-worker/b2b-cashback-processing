package com.noproject.common

import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.config.syntax._

object ConfigUtil {

  def decodeUltimately[T: Decoder](c: Config): T  = {
    c.as[T].fold(
      err => throw new RuntimeException(s"Can't parse config, reason: '${err.getMessage}'"),
      res => res
    )
  }

  def decodeUltimately[T: Decoder](c: Config, path: String): T  = {
    c.as[T](path).fold(
      err => throw new RuntimeException(s"Can't parse config, reason: '${err.getMessage}'"),
      res => res
    )
  }

}
