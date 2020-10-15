package com.noproject.partner.coupilia

import java.time.LocalDate

import cats.effect.{IO, Resource}
import com.google.inject.Guice
import com.noproject.common.Executors
import com.noproject.common.app.{CommonApp, ResourcefulApp}
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer, CommonS2sServer}
import com.noproject.common.domain.{LevelDBPersistence, DefaultPersistence}
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.MerchantMappingDataService
import com.noproject.common.stream.{RabbitProducer, StreamEvent}
import com.noproject.common.stream.impl.ImmediateRabbitProducer
import com.noproject.partner.coupilia.domain.model.CoupiliaTxn
import com.noproject.partner.coupilia.service.CoupiliaIntegrationService
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import fs2.concurrent.Topic
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.metrics.prometheus.PrometheusExportService
import shapeless.{::, HNil}

import scala.concurrent.duration._

object Application extends CommonApp with ResourcefulApp {

  override protected type Resources = (
     DefaultPersistence
  :: RabbitProducer[CashbackTransaction]
  :: Client[IO]
  :: LevelDBPersistence[CashbackTransaction]
  :: Topic[IO, StreamEvent[CoupiliaTxn]]
  :: HNil
  )

  override protected def createResources(
    pes:  PrometheusExportService[IO]
  ): Resource[IO, Resources] = for {
    pres  <- DefaultPersistence.transactor(dbConfig, Some(pes.collectorRegistry))
    http  <- buildHttpClient(4)
    fs2r  <- buildRabbit
    tprod <- ImmediateRabbitProducer.getInstance[CashbackTransaction]("txns", fs2r)
    ldb   <- LevelDBPersistence.getInstance[CashbackTransaction]("lib/defaultdb-coupilia")
    topic <- buildTopic[CoupiliaTxn]
  } yield pres :: tprod :: http :: ldb :: topic :: HNil


  override protected def createServices(res: Resources, pes: PrometheusExportService[IO]): List[IO[_]] = {
    val (pers ::  rprod :: http :: ldb :: topic :: HNil) = res
    val injector   = Guice.createInjector(new CoupiliaModule(this.timer, parentConfig, envMode, pers, rprod, http, pes, topic, ldb))
    val service    = injector.getInstance(classOf[CoupiliaIntegrationService])
    val server     = injector.getInstance(classOf[CommonApiServer])
    val prometheus = injector.getInstance(classOf[CommonPrometheusServer])
    val s2sServer  = injector.getInstance(classOf[CommonS2sServer])
    val mmds       = injector.getInstance(classOf[MerchantMappingDataService])

    // FIXME: set appropriate time range
    def fetchFrom: LocalDate = { LocalDate.now().minusDays(1) }
    def fetchTo: LocalDate = { LocalDate.now() }

//    val stream1 = fs2.Stream.fixedDelay(TXN_SYNC_DELAY minutes)
//      .evalMap(_ => service.syncCoupiliaTxns(fetchFrom, fetchTo) )
//
//    val stream2 = fs2.Stream.fixedDelay(OFFER_SYNC_DELAY minutes)
//      .evalMap(_ => service.syncCoupiliaOffers)

    List(
      service.runForever
//      stream1.compile.drain
//    , stream2.compile.drain
    , server.runForever
    , prometheus.runForever(pes.routes)
    , s2sServer.runForever
    )
  }

}
