package com.noproject.partner.button

import cats.effect.{ContextShift, IO, Timer}
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}
import com.noproject.common.app.CommonModule
import com.noproject.common.config._
import com.noproject.common.controller.route._
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.{LevelDBPersistence, DefaultPersistence}
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.stream.{RabbitProducer, StreamEvent}
import com.noproject.partner.button.config.{UsebuttonConfig, UsebuttonConfigProvider}
import com.noproject.partner.button.domain.model.UsebuttonPayload
import com.noproject.partner.button.route.{InternalRoute, TestRoute, UsebuttonWebhookRoute}
import com.typesafe.config.Config
import fs2.concurrent.Topic
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.http4s.client.Client
import org.http4s.metrics.prometheus.PrometheusExportService

class UsebuttonModule(
  timer:    Timer[IO]
, config:   Config
, envMode:  EnvironmentMode
, sp:       DefaultPersistence
, qrp:      RabbitProducer[CashbackTransaction]
, erp:      RabbitProducer[EventLogItem]
, pes:      PrometheusExportService[IO]
, http:     Client[IO]
, topic:    Topic[IO, StreamEvent[UsebuttonPayload]]
, ldb:      LevelDBPersistence[CashbackTransaction]
)(implicit cs: ContextShift[IO]) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    install(new CommonModule(timer, config, envMode, sp, pes))

    bind(new TypeLiteral[ConfigProvider[UsebuttonConfig]](){}).to(classOf[UsebuttonConfigProvider])
    bind(new TypeLiteral[ConfigProvider[OffersConfig]](){}).to(classOf[OffersConfigProvider])
    bind(new TypeLiteral[RabbitProducer[CashbackTransaction]](){}).toInstance(qrp)
    bind(new TypeLiteral[RabbitProducer[EventLogItem]](){}).toInstance(erp)
    bind(new TypeLiteral[LevelDBPersistence[CashbackTransaction]](){}).toInstance(ldb)
    bind(new TypeLiteral[Topic[IO, StreamEvent[UsebuttonPayload]]](){}).toInstance(topic)
    bind(new TypeLiteral[Client[IO]](){}).toInstance(http)

    val multiBinding = ScalaMultibinder.newSetBinder[Routing](binder)
    multiBinding.addBinding.to[HealthCheckRoute]
    multiBinding.addBinding.to[UsebuttonWebhookRoute]
    multiBinding.addBinding.to[TestRoute]
    val multiBindingS2s = ScalaMultibinder.newSetBinder[S2sRouting](binder)
    multiBindingS2s.addBinding.to[InternalRoute]
    bind[Int].annotatedWith(Names.named("httpPort")).toInstance(config.getInt("http.port"))
    bind[Int].annotatedWith(Names.named("prometheusPort")).toInstance(config.getInt("prometheus.port"))
    bind[Int].annotatedWith(Names.named("s2sPort")).toInstance(config.getInt("s2s.port"))
    bind[String].annotatedWith(Names.named("s2sUser")).toInstance(config.getString("s2s.user"))
    bind[String].annotatedWith(Names.named("s2sPassword")).toInstance(config.getString("s2s.password"))
    bind[CommonApiServer]
    bind[CommonPrometheusServer]
    bind[CommonS2sServer]

  }

}
