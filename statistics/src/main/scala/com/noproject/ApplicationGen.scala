package com.noproject

import cats.effect.{IO, Resource}
import com.google.inject.Guice
import com.noproject.common.app.{CommonApp, ResourcefulApp}
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer}
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.model.eventlog.EventLogObjectType.CashbackTxn
import com.noproject.common.domain.service.EventLogDataService
import com.noproject.common.stream.RabbitConsuming
import com.noproject.service.{StatisticsService, WeightedConsumerGeneratorService}
import org.http4s.metrics.prometheus.PrometheusExportService
import shapeless.{::, HNil}

object ApplicationGen extends CommonApp with ResourcefulApp {

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
    val sm = new GenModule(this.timer, parentConfig, envMode, sp, pes)
    val injector = Guice.createInjector(sm)

    val genService = injector.getInstance(classOf[WeightedConsumerGeneratorService])

    List(  genService.run(10000)  )
  }

}
