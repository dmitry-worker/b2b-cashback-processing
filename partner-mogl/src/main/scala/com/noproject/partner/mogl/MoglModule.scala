package com.noproject.partner.mogl

import java.time.Clock

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}
import com.noproject.common.app.CommonModule
import com.noproject.common.config._
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer, HealthCheckRoute, Routing}
import com.noproject.common.domain.{LevelDBPersistence, DefaultPersistence}
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.stream.{RabbitProducer, StreamEvent}
import com.noproject.partner.mogl.config.{MoglConfig, MoglConfigProvider}
import com.noproject.partner.mogl.model.MoglTxn
import com.noproject.partner.mogl.route.{MoglWebhookRoute, TestRoute}
import com.typesafe.config.Config
import fs2.concurrent.Topic
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.http4s.client.Client
import org.http4s.metrics.prometheus.PrometheusExportService

class MoglModule(
  timer:   Timer[IO]
, config:  Config
, envMode: EnvironmentMode
, sp:      DefaultPersistence
, qrp:     RabbitProducer[CashbackTransaction]
, pes:     PrometheusExportService[IO]
, http:    Client[IO]
, topic:   Topic[IO, StreamEvent[MoglTxn]]
, ldb:     LevelDBPersistence[CashbackTransaction]
)(implicit cs: ContextShift[IO]) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    install(new CommonModule(timer, config, envMode, sp, pes))

    bind(new TypeLiteral[ConfigProvider[MoglConfig]](){}).to(classOf[MoglConfigProvider])
    bind(new TypeLiteral[ConfigProvider[OffersConfig]](){}).to(classOf[OffersConfigProvider])
    bind(new TypeLiteral[RabbitProducer[CashbackTransaction]](){}).toInstance(qrp)
    bind(new TypeLiteral[LevelDBPersistence[CashbackTransaction]](){}).toInstance(ldb)
    bind(new TypeLiteral[Topic[IO, StreamEvent[MoglTxn]]](){}).toInstance(topic)
    bind(new TypeLiteral[Client[IO]](){}).toInstance(http)

    val multiBinding = ScalaMultibinder.newSetBinder[Routing](binder)
    multiBinding.addBinding.to[HealthCheckRoute]
    multiBinding.addBinding.to[TestRoute]
    multiBinding.addBinding.to[MoglWebhookRoute]
    bind[Int].annotatedWith(Names.named("httpPort")).toInstance(config.getInt("http.port"))
    bind[Int].annotatedWith(Names.named("prometheusPort")).toInstance(config.getInt("prometheus.port"))
    bind[CommonApiServer]
    bind[CommonPrometheusServer]
  }
}
