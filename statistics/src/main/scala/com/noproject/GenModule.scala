package com.noproject

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.google.inject.{AbstractModule, TypeLiteral}
import com.google.inject.name.Names
import com.noproject.common.app.CommonModule
import com.noproject.common.config.{ConfigProvider, EnvironmentMode, FailFastConfigProvider, OffersConfig, OffersConfigProvider}
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer, HealthCheckRoute, Routing}
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.service.{CashbackTransactionDataService, ConsumerDataService, MerchantDataService}
import com.typesafe.config.Config
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.http4s.metrics.prometheus.PrometheusExportService

class GenModule(
  timer: Timer[IO]
, config: Config
, envMode: EnvironmentMode
, sp: DefaultPersistence
, pes: PrometheusExportService[IO]
)(implicit cs: ContextShift[IO]) extends AbstractModule with ScalaModule {

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  override def configure(): Unit = {
    install(new CommonModule(timer, config, envMode, sp, pes))
    bind[Clock].toInstance(Clock.systemUTC())
    bind(new TypeLiteral[ConfigProvider[OffersConfig]](){}).toInstance(StaticOffersConfigProvider)
    bind[CashbackTransactionDataService]
    bind[MerchantDataService]
    bind[ConsumerDataService]
  }

}
