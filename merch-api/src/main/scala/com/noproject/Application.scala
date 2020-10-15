package com.noproject

import java.time.Clock

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits._
import com.google.inject.Guice
import com.noproject.common.app.{CommonApp, ResourcefulApp}
import com.noproject.common.cache.KeyValueCache
import com.noproject.common.domain.model.customer.Consumer
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import com.noproject.common.domain.{DbConfig, DefaultPersistence}
import com.noproject.common.stream.impl.ConsumerQueue
import com.noproject.common.stream.{GroupingQueueResource, SimpleLoopStream}
import com.noproject.controller.Server
import com.noproject.domain.dao.SchemaMigration
import com.typesafe.config.ConfigFactory
import org.http4s.client.Client
import org.http4s.metrics.prometheus.PrometheusExportService
import shapeless.{::, HNil}

object Application extends CommonApp with ResourcefulApp {

  // db migration
  lazy val migration = new SchemaMigration(dbConfig)
  migration.migrateBaseline()

  override protected def createServices(resources: Resources, pes: PrometheusExportService[IO]): List[IO[_]] = {
    val (persistence :: cache :: consq :: http :: HNil ) = resources
    val mm        = new MerchApiModule(this.timer, this.contextShift, pes, persistence, cache, consq, Clock.systemUTC(), parentConfig, http)
    val injector  = Guice.createInjector(mm)
    val server    = injector.getInstance(classOf[Server])
    val consQueue = injector.getInstance(classOf[ConsumerQueue])

    val _         = Scheduler.run(injector)

    val serverStream = server.start
    val queueStream  = consQueue.runForever
    List(serverStream, queueStream)
  }

  override protected type Resources = (
      DefaultPersistence
  ::  KeyValueCache[String, MerchantOfferRow]
  ::  GroupingQueueResource[Consumer]
  ::  Client[IO]
  ::  HNil
  )

  override protected def createResources(pes: PrometheusExportService[IO]): Resource[IO, Resources] = {
    for {
      pres  <- DefaultPersistence.transactor(dbConfig, Some(pes.collectorRegistry))
      cache <- KeyValueCache[String, MerchantOfferRow](1000 * 60, Clock.systemUTC(), None)
      http  <- buildHttpClient()
      consq <- GroupingQueueResource.getInstance[Consumer](50, 2)
    } yield pres :: cache :: consq :: http :: HNil
  }

}
