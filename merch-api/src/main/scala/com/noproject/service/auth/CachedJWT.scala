package com.noproject.service.auth

import cats.effect.IO
import cats.effect.concurrent.Ref
import com.noproject.common.config.ConfigProvider
import com.noproject.common.security.JWT
import com.noproject.config.AuthConfig


// TODO: maybe make it a separate class and provide as an argument for services?
trait CachedJWT {

  protected def authCP: ConfigProvider[AuthConfig]

  private val ref: Ref[IO, (AuthConfig, JWT)] = {
    val io = for {
      conf    <- authCP.getConfig
      jwt      = new JWT(conf.key, conf.expirationMinutes)
      result  <- Ref.of[IO, (AuthConfig, JWT)](conf -> jwt)
    } yield result
    io.unsafeRunSync()
  }

  def getCurrentJWT: IO[JWT] = {
    def updateJwt(conf: AuthConfig): IO[JWT] = {
      val jwt = new JWT(conf.key, conf.expirationMinutes)
      ref.set( conf -> jwt ).map( _ => jwt )
    }
    for {
      actualConf  <- authCP.getConfig
      tuple2      <- ref.get
      (conf, jwt) =  tuple2
      actualJWT   <- if (conf == actualConf) IO.pure(jwt) else updateJwt(conf)
    } yield actualJWT
  }

}
