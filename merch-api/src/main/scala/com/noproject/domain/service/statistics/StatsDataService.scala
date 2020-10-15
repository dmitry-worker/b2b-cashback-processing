package com.noproject.domain.service.statistics

import cats.effect.IO
import com.noproject.common.Executors
import com.noproject.controller.dto.dashboard.DashboardStatsRequest
import com.noproject.domain.dao.statistics.StatsDAO
import com.noproject.domain.model.statistics.{CasbackTransactionStats, OfferStats}
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

@Singleton
class StatsDataService @Inject()(dao: StatsDAO) {

  implicit val ec: ExecutionContext = Executors.dbExec

  def getOfferStats: IO[OfferStats] = {
    for {
      offs <- dao.getOfferStats
    } yield {
      offs.getOrElse(OfferStats())
    }
  }

  def getTxnStats(dsr: DashboardStatsRequest, customer: Option[String]): IO[CasbackTransactionStats] = {
    for {
      txns <- dao.getTransactionStats(dsr, customer)
    } yield {
      txns.getOrElse(CasbackTransactionStats())
    }
  }

  def refreshTxnStats = {
    for {
      _ <- dao.refreshTxns
    } yield ()
  }
}
