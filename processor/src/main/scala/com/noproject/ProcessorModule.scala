package com.noproject

import cats.effect.{ContextShift, IO, Timer}
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}
import com.noproject.common.app.CommonModule
import com.noproject.common.config.{ConfigProvider, EnvironmentMode}
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer, HealthCheckRoute, Routing}
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.stream.{RabbitConsuming, RabbitProducer}
import com.noproject.config.{TxnDeliveryConfig, TxnDeliveryConfigProvider}
import com.noproject.processor.route.TestRoute
import com.typesafe.config.Config
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.http4s.client.Client
import org.http4s.metrics.prometheus.PrometheusExportService

class ProcessorModule(
  config:   Config
, envMode:  EnvironmentMode
, sp:       DefaultPersistence
, eprod:    RabbitProducer[EventLogItem]
, tprod:    RabbitProducer[CashbackTransaction]
, tcons:    RabbitConsuming[CashbackTransaction]
, pes:      PrometheusExportService[IO]
, client:   Client[IO]
)(implicit cs: ContextShift[IO], timer: Timer[IO]) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    install(new CommonModule(timer, config, envMode, sp, pes))

    bind(new TypeLiteral[Client[IO]](){}).toInstance(client)
    bind(new TypeLiteral[ConfigProvider[TxnDeliveryConfig]](){}).to(classOf[TxnDeliveryConfigProvider])
    bind(new TypeLiteral[RabbitProducer[EventLogItem]](){}).toInstance(eprod)
    bind(new TypeLiteral[RabbitProducer[CashbackTransaction]](){}).toInstance(tprod)
    bind(new TypeLiteral[RabbitConsuming[CashbackTransaction]](){}).toInstance(tcons)

    bind[String].annotatedWith(Names.named("customerName")).toInstance(config.getString("customer"))

    val multiBinding = ScalaMultibinder.newSetBinder[Routing](binder)
    multiBinding.addBinding.to[HealthCheckRoute]
    multiBinding.addBinding.to[TestRoute]
    bind[Int].annotatedWith(Names.named("httpPort")).toInstance(config.getInt("http.port"))
    bind[Int].annotatedWith(Names.named("prometheusPort")).toInstance(config.getInt("prometheus.port"))
    bind[CommonApiServer]
    bind[CommonPrometheusServer]
  }

}
