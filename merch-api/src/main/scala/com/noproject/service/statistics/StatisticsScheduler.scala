package com.noproject.service.statistics

import java.time.Clock
import java.time.temporal.ChronoUnit

import cats.effect.{IO, Timer}
import com.noproject.common.domain.dao.merchant.{MerchantOfferDAO, MerchantOfferFreshness}
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.logging.DefaultLogging
import com.noproject.config.SchedulerConfig
import com.noproject.domain.service.statistics.StatsDataService
import cron4s.Cron
import eu.timepit.fs2cron.awakeEveryCron
import fs2.Stream
import io.prometheus.client.{CollectorRegistry, Gauge}
import javax.inject.{Inject, Singleton}

import scala.concurrent.duration._

@Singleton
class StatisticsScheduler @Inject()(
  ds:       StatsDataService
, conf:     SchedulerConfig
, cr:       CollectorRegistry
, offerDAO: MerchantOfferDAO
, netDAO:   NetworkDAO
, clock:    Clock
) extends DefaultLogging {


  private def updateStats: IO[Unit] = {
    for {
      _     <- ds.refreshTxnStats
      f     <- offerDAO.getFreshnessData
      nets  <- netDAO.findAll
      _     <- updateMetrics(nets.map(_.name), f)
    } yield logger.info("Stats updated")
  }


  def scheduleStatsUpdate(implicit timer: Timer[IO]): Unit = {
    val cron      = Cron.unsafeParse(conf.statisticsCron)
    val job       = Stream.eval(updateStats)
    val scheduled = awakeEveryCron[IO](cron) >> job
    scheduled.compile.drain.unsafeRunAsync {
      case Left(ex) =>
        logger.error(ex.getMessage)
      case _ => // no action
    }
  }

  // metrics related stuff
  private val InfDuration = 30.days.toSeconds

  private lazy val freshnessGauge = Gauge
    .build()
    .name("offers_freshness")
    .help(s"Seconds from last offer update by network")
    .labelNames("network")
    .register(cr)


  private def updateMetrics(networks: List[String], f: MerchantOfferFreshness): IO[Unit] = IO.delay {
    val now = clock.instant
    networks.map { name =>
      val value = f.contents.get(name)
        .map(ChronoUnit.SECONDS.between(_, now))
        .getOrElse(InfDuration)
      freshnessGauge.labels(name).set(value)
    }
  }



}
