package com.noproject

import cats.effect.{ContextShift, IO, Resource}
import com.google.inject.Guice
import com.noproject.common.app.{CommonApp, ResourcefulApp}
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer}
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.model.eventlog.{EventLogItem, EventLogObjectType}
import com.noproject.common.domain.service.EventLogDataService
import com.noproject.common.stream.RabbitConsuming
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.eventlog.EventLogObjectType.CashbackTxn
import com.noproject.service.StatisticsService
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import org.http4s.metrics.prometheus.PrometheusExportService
import shapeless.{::, HNil}

object Application extends CommonApp with ResourcefulApp {

  override protected type Resources = DefaultPersistence :: RabbitConsuming[EventLogItem] :: HNil

  override protected def createResources(
    pes: PrometheusExportService[IO]
  ): Resource[IO, Resources] = for {
    pres  <- DefaultPersistence.transactor(dbConfig, Some(pes.collectorRegistry))
    qn     = s"events:${CashbackTxn.entryName}"
    fs2r  <- buildRabbit
    rcons <- RabbitConsuming.getInstance[EventLogItem](qn, fs2r)
  } yield pres :: rcons :: HNil


  override protected def createServices(res: Resources, pes: PrometheusExportService[IO]): List[IO[_]] = {
    val (sp :: rcons ::  HNil ) = res
    val sm = new StatisticsModule(this.timer, parentConfig, envMode, sp, pes)
    val injector = Guice.createInjector(sm)

    val elds       = injector.getInstance(classOf[EventLogDataService])
    val server     = injector.getInstance(classOf[CommonApiServer])
    val prometheus = injector.getInstance(classOf[CommonPrometheusServer])

    val service = new StatisticsService(rcons, elds)

    List(
      server.runForever
    , service.runForever
    , prometheus.runForever(pes.routes)
    )
  }


}
