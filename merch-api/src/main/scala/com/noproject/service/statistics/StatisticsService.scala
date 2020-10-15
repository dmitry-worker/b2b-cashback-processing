package com.noproject.service.statistics

import java.time.Duration
import java.time.temporal.ChronoUnit

import cats.effect.IO
import com.noproject.config.SchedulerConfig
import com.noproject.controller.dto.dashboard.{DashboardOffersStatsResponse, DashboardStatsRequest, DashboardTransactionsStatsResponse}
import com.noproject.domain.service.statistics.StatsDataService
import javax.inject.{Inject, Singleton}

@Singleton
class StatisticsService @Inject()(ds: StatsDataService, conf: SchedulerConfig) {

  def collectOffers: IO[DashboardOffersStatsResponse] = {
    for {
      res <- ds.getOfferStats
    } yield DashboardOffersStatsResponse(res)
  }

  def collectTxns(dsr: DashboardStatsRequest, customer: Option[String]): IO[DashboardTransactionsStatsResponse] = {
    require(dsr.beginsAt.isDefined && dsr.endsAt.isDefined)
    val diff         = Duration.between(dsr.beginsAt.get, dsr.endsAt.get)
    val prevEndsAt   = dsr.beginsAt.map(_.minus(1, ChronoUnit.DAYS))
    val prevBeginsAt = prevEndsAt.map(_.minus(diff))
    val prevDsr      = dsr.copy(beginsAt = prevBeginsAt, endsAt = prevEndsAt)

    for {
      curr <- ds.getTxnStats(dsr, customer)
      prev <- ds.getTxnStats(prevDsr, customer)
    } yield DashboardTransactionsStatsResponse(curr, prev)
  }
}
