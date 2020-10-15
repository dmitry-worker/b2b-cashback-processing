package com.noproject


import cats.effect._
import com.google.inject.{Guice, Key}
import com.noproject.common.Executors
import com.noproject.common.app.{CommonApp, ResourcefulApp}
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer}
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.model.eventlog.{EventLogItem, Loggable}
import com.noproject.common.stream.{HandledLoopStream, LogProducer, RabbitConsuming, RabbitProducer}
import com.noproject.common.stream.impl.ImmediateRabbitProducer
import com.noproject.service.{TxnDeliveryService, TxnSubscriberService}
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.metrics.prometheus.PrometheusExportService
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.transaction.CashbackTransaction
import shapeless.{::, HNil}

object Application extends CommonApp with ResourcefulApp {

  override protected type Resources = (
      DefaultPersistence
   :: RabbitProducer[EventLogItem]
   :: RabbitProducer[CashbackTransaction]
   :: RabbitConsuming[CashbackTransaction]
   :: Client[IO]
   :: HNil
  )

  override protected def createResources(
    pes:  PrometheusExportService[IO]
  ): Resource[IO, Resources] = for {
    fs2r  <- buildRabbit
    pres  <- DefaultPersistence.transactor(dbConfig, Some(pes.collectorRegistry))
    http  <- buildHttpClient()
    eprod <- ImmediateRabbitProducer.getInstance[EventLogItem]("events", fs2r)
    cust   = parentConfig.getString("customer")
    tcons <- RabbitConsuming.getInstance[CashbackTransaction](s"txns:${cust}", fs2r)
    // TODO: remove: this one is for test route only
    tprod <- ImmediateRabbitProducer.getInstance[CashbackTransaction]("txns", fs2r)
  } yield pres :: eprod :: tprod :: tcons :: http :: HNil

  override protected def createServices(res: Resources, pes: PrometheusExportService[IO]): List[IO[_]] = {
    val (sp :: eprod :: tprod :: tcons :: http :: HNil ) = res
    val mm = new ProcessorModule(parentConfig, envMode, sp, eprod, tprod, tcons, pes, http)
    val injector = Guice.createInjector(mm)

    val txnDelivery   = injector.getInstance(classOf[TxnDeliveryService])
    val txnSubscriber = injector.getInstance(classOf[TxnSubscriberService])
    val server        = injector.getInstance(classOf[CommonApiServer])
    val prometheus    = injector.getInstance(classOf[CommonPrometheusServer])

    List(
      txnSubscriber.runForever
    , txnDelivery.runForever
    , server.runForever
    , prometheus.runForever(pes.routes)
    )
  }

}
