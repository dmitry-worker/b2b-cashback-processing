package com.noproject.stress

import java.time.Clock
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.http4s.client.Client

import cats.implicits._
import cats.effect.{ContextShift, IO, Timer}

import com.google.inject.name.Names
import com.google.inject.TypeLiteral

import com.noproject.common.app.CommonModule
import com.noproject.common.config._
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}


class TestServerModule(
  timer:    Timer[IO]
, config:   Config
, sp:       DefaultPersistence
, http:     Client[IO]
) extends ScalaModule {

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }


  override def configure(): Unit = {
    bind(new TypeLiteral[Client[IO]](){}).toInstance(http)
    bind(new TypeLiteral[ConfigProvider[OffersConfig]](){}).toInstance(StaticOffersConfigProvider)
    bind[Clock].toInstance(Clock.systemUTC)
    bind[DefaultPersistence].toInstance(sp)
  }

}
