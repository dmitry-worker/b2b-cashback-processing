package com.noproject

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.google.inject.{AbstractModule, TypeLiteral}
import com.noproject.common.cache.KeyValueCache
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.model.customer.Consumer
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import com.noproject.common.stream.GroupingQueueResource
import com.noproject.modules._
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.http4s.client.Client
import org.http4s.metrics.prometheus.PrometheusExportService

class MerchApiModule(
  timer:        Timer[IO]
, cs:           ContextShift[IO]
, pes:          PrometheusExportService[IO]
, pers:         DefaultPersistence
, cache:        KeyValueCache[String, MerchantOfferRow]
, gqr:          GroupingQueueResource[Consumer]
, clock:        Clock
, parentConfig: Config
, http:         Client[IO]
) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    bind[Clock].toInstance(clock)
    bind(new TypeLiteral[PrometheusExportService[IO]](){}).toInstance(pes)
    bind(new TypeLiteral[Timer[IO]](){}).toInstance(timer)
    bind(new TypeLiteral[ContextShift[IO]](){}).toInstance(cs)
    bind(new TypeLiteral[GroupingQueueResource[Consumer]](){}).toInstance(gqr)
    bind(new TypeLiteral[Client[IO]](){}).toInstance(http)

    val envMode = EnvironmentMode.withNameInsensitive(parentConfig.getString("build.envMode"))
    bind[EnvironmentMode].toInstance(envMode)

    install(new DomainModule(pers))
    install(new ConfigModule(pers, parentConfig)(implicitly(cs)))
    install(new RoutingModule(envMode))
    install(new AuthModule)
    install(new TrackingModule(cache))
    install(new PartnerModule)
  }
}
