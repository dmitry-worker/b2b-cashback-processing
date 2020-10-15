package com.noproject

import cats.effect.{ContextShift, IO, Timer}
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}
import com.noproject.common.app.CommonModule
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer, HealthCheckRoute, Routing}
import com.noproject.common.domain.DefaultPersistence
import com.typesafe.config.Config
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.http4s.metrics.prometheus.PrometheusExportService

class StatisticsModule(
  timer: Timer[IO]
, config: Config
, envMode: EnvironmentMode
, sp: DefaultPersistence
, pes: PrometheusExportService[IO]
)(implicit cs: ContextShift[IO]) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    install(new CommonModule(timer, config, envMode, sp, pes))

    val multiBinding = ScalaMultibinder.newSetBinder[Routing](binder)
    multiBinding.addBinding.to[HealthCheckRoute]
    bind[Int].annotatedWith(Names.named("httpPort")).toInstance(config.getInt("http.port"))
    bind[Int].annotatedWith(Names.named("prometheusPort")).toInstance(config.getInt("prometheus.port"))
    bind[CommonApiServer]
    bind[CommonPrometheusServer]
  }

}
