package com.noproject.partner.coupilia

import cats.effect.{ContextShift, IO, Timer}
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import com.noproject.common.app.CommonModule
import com.noproject.common.config.{ConfigProvider, EnvironmentMode, OffersConfig, OffersConfigProvider}
import com.noproject.common.controller.route._
import com.noproject.common.domain.{LevelDBPersistence, DefaultPersistence}
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.stream.{RabbitProducer, StreamEvent}
import com.noproject.partner.coupilia.config.{CoupiliaConfig, CoupiliaConfigProvider}
import com.noproject.partner.coupilia.domain.model.CoupiliaTxn
import com.noproject.partner.coupilia.route.{InternalRoute, TestRoute}
import com.noproject.partner.coupilia.service.CoupiliaIntegrationService
import com.typesafe.config.Config
import fs2.concurrent.Topic
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.http4s.client.Client
import org.http4s.metrics.prometheus.PrometheusExportService

class CoupiliaModule(
  timer:    Timer[IO]
, config:   Config
, envMode:  EnvironmentMode
, sp:       DefaultPersistence
, qrp:      RabbitProducer[CashbackTransaction]
, http:     Client[IO]
, pes:      PrometheusExportService[IO]
, topic:    Topic[IO, StreamEvent[CoupiliaTxn]]
, ldb:      LevelDBPersistence[CashbackTransaction]
)(implicit cs: ContextShift[IO]) extends ScalaModule {

  override def configure(): Unit = {

    install(new CommonModule(timer, config, envMode, sp, pes))

    bind(new TypeLiteral[ConfigProvider[OffersConfig]](){}).to(classOf[OffersConfigProvider])
    bind(new TypeLiteral[ConfigProvider[CoupiliaConfig]](){}).to(classOf[CoupiliaConfigProvider])
    bind(new TypeLiteral[RabbitProducer[CashbackTransaction]](){}).toInstance(qrp)
    bind(new TypeLiteral[LevelDBPersistence[CashbackTransaction]](){}).toInstance(ldb)
    bind(new TypeLiteral[Topic[IO, StreamEvent[CoupiliaTxn]]](){}).toInstance(topic)
    bind(new TypeLiteral[Client[IO]](){}).toInstance(http)

    bind[CoupiliaIntegrationService]

    val multiBinding = ScalaMultibinder.newSetBinder[Routing](binder)
    multiBinding.addBinding.to[HealthCheckRoute]
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
