package com.noproject.common.app

import java.time.Clock

import cats.effect._
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import com.noproject.common.config._
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.service.{MerchantMappingDataService, MerchantMappingDataServiceImpl}
import com.typesafe.config.Config
import io.prometheus.client.CollectorRegistry
import net.codingwell.scalaguice.ScalaModule
import org.http4s.metrics.prometheus.PrometheusExportService

class CommonModule(
  timer: Timer[IO]
, config: Config
, envMode: EnvironmentMode
, sp: DefaultPersistence
, pes: PrometheusExportService[IO]
)(implicit cs: ContextShift[IO]) extends ScalaModule {

  override def configure(): Unit = {
    bind(new TypeLiteral[ContextShift[IO]]() {}).toInstance(cs)
    bind(new TypeLiteral[Concurrent[IO]]() {}).toInstance(implicitly[Concurrent[IO]])
    bind(new TypeLiteral[PrometheusExportService[IO]]() {}).toInstance(pes)
    bind(new TypeLiteral[Timer[IO]]() {}).toInstance(timer)
    bind[MerchantMappingDataService].to(classOf[MerchantMappingDataServiceImpl])
    bind[CollectorRegistry].toInstance(pes.collectorRegistry)
    bind[Clock].toInstance(Clock.systemUTC())
    bind[Config].toInstance(config)
    bind[DefaultPersistence].toInstance(sp)
    bind[EnvironmentMode].toInstance(envMode)

    val appVersion = config.getString("build.version")
    bind[String].annotatedWith(Names.named("appVersion")).toInstance(appVersion)
  }
}
