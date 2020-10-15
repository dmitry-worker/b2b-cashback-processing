package com.noproject.partner.button

import cats.effect._
import com.google.inject.Guice
import com.noproject.common.Executors
import com.noproject.common.app.{CommonApp, ResourcefulApp}
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.route.{CommonApiServer, CommonPrometheusServer, CommonS2sServer}
import com.noproject.common.domain.{LevelDBPersistence, DefaultPersistence}
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.stream.{RabbitProducer, StreamEvent}
import com.noproject.common.stream.impl.ImmediateRabbitProducer
import com.noproject.partner.button.domain.model.UsebuttonPayload
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import fs2.concurrent.Topic
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.metrics.prometheus.PrometheusExportService
import shapeless.{::, HNil}

import scala.concurrent.duration._

object Application extends CommonApp with ResourcefulApp {

  val OFFER_SYNC_DELAY = if (envMode == EnvironmentMode.Test) 1 else 60 // TODO move to conf

  override protected type Resources = (
     DefaultPersistence
  :: RabbitProducer[CashbackTransaction]
  :: RabbitProducer[EventLogItem]
  :: Client[IO]
  :: LevelDBPersistence[CashbackTransaction]
  :: Topic[IO, StreamEvent[UsebuttonPayload]]
  :: HNil
  )

  override protected def createResources(pes:  PrometheusExportService[IO]
  ): Resource[IO, Resources] = for {
    pres  <- DefaultPersistence.transactor(dbConfig, Some(pes.collectorRegistry))
    http  <- buildHttpClient(4)
    fs2r  <- buildRabbit
    tprod <- ImmediateRabbitProducer.getInstance[CashbackTransaction]("txns", fs2r)
    eprod <- ImmediateRabbitProducer.getInstance[EventLogItem]("events", fs2r)
    ldb   <- LevelDBPersistence.getInstance[CashbackTransaction]("lib/defaultdb-usebutton")
    topic <- buildTopic[UsebuttonPayload]
  } yield pres :: tprod :: eprod :: http :: ldb :: topic :: HNil




  override protected def createServices(res: Resources, pes: PrometheusExportService[IO]): List[IO[_]] = {
    val (sp ::  sr :: er :: http :: ldb :: topic :: HNil ) = res
    val injector   = Guice.createInjector(new UsebuttonModule(this.timer, parentConfig, envMode, sp, sr, er, pes, http, topic, ldb))
    val service    = injector.getInstance(classOf[UseButtonIntegrationService])
    val server     = injector.getInstance(classOf[CommonApiServer])
    val prometheus = injector.getInstance(classOf[CommonPrometheusServer])
    val s2sServer  = injector.getInstance(classOf[CommonS2sServer])

    val stream: fs2.Stream[IO, Unit] = fs2.Stream
      .fixedDelay(OFFER_SYNC_DELAY minutes)
      .evalMap(_ => service.syncUsebuttonOffersWithRestApi)

    List(
      stream.compile.drain
    , server.runForever
    , service.runForever
    , prometheus.runForever(pes.routes)
    , s2sServer.runForever
    )
  }
}
