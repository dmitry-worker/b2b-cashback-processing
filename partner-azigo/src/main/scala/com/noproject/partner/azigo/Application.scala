package com.noproject.partner.azigo

import java.time.Instant

import cats.effect.{IO, Resource}
import com.google.inject.Guice
import com.noproject.common.app.{CommonApp, ResourcefulApp}
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer, CommonS2sServer}
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.MerchantMappingDataService
import com.noproject.common.domain.{LevelDBPersistence, DefaultPersistence}
import com.noproject.common.stream.RabbitProducer
import com.noproject.common.stream.impl.ImmediateRabbitProducer
import com.noproject.common.stream.{GroupingQueueResource, RabbitConfig, RabbitProducer, StreamEvent}
import com.noproject.partner.azigo.domain.model.AzigoTxn
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import fs2.concurrent.Topic
import org.http4s.client.Client
import org.http4s.metrics.prometheus.PrometheusExportService
import shapeless.{::, HNil}

import scala.concurrent.duration._

object Application extends CommonApp with ResourcefulApp {

  val OFFER_SYNC_DELAY = if (envMode == EnvironmentMode.Test) 1 else 60
  val TXN_SYNC_DELAY   = if (envMode == EnvironmentMode.Test) 2 else 60

  override protected type Resources = (
     DefaultPersistence
  :: RabbitProducer[CashbackTransaction]
  :: Client[IO]
  :: LevelDBPersistence[CashbackTransaction]
  :: Topic[IO, StreamEvent[AzigoTxn]]
  :: HNil
  )

  override protected def createResources(pes:  PrometheusExportService[IO]): Resource[IO, Resources] = for {
    pres  <- DefaultPersistence.transactor(dbConfig, Some(pes.collectorRegistry))
    http  <- buildHttpClient(4)
    fs2r  <- buildRabbit
    tprod <- ImmediateRabbitProducer.getInstance[CashbackTransaction]("txns", fs2r)
    ldb   <- LevelDBPersistence.getInstance[CashbackTransaction]("lib/defaultdb-azigo")
    topic <- buildTopic[AzigoTxn]
  } yield pres :: tprod :: http :: ldb :: topic :: HNil

  override protected def createServices(res: Resources, pes: PrometheusExportService[IO]): List[IO[_]] = {
    val (pers ::  tprod :: http :: ldb :: topic :: HNil ) = res
    val injector   = Guice.createInjector(new AzigoModule(this.timer, parentConfig, envMode, pers, tprod, pes, http, topic, ldb))
    val service    = injector.getInstance(classOf[AzigoIntegrationService])
    val server     = injector.getInstance(classOf[CommonApiServer])
    val prometheus = injector.getInstance(classOf[CommonPrometheusServer])
    val s2sServer  = injector.getInstance(classOf[CommonS2sServer])

    def fetchFrom: Instant = { Instant.now().minusSeconds(3600) }
    def fetchTo: Instant = { Instant.now() }

    val stream1 = service.runForever

    val stream2 = fs2.Stream.fixedDelay(OFFER_SYNC_DELAY minutes)
      .evalMap(_ => service.syncAzigoOffers)
      .compile.drain

    List(
      stream1
//    , stream2
    , server.runForever
    , prometheus.runForever(pes.routes)
    , s2sServer.runForever
    )
  }

}
