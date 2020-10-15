package com.noproject.domain.dao.statistics

import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.controller.dto.dashboard.DashboardStatsRequest
import com.noproject.domain.model.statistics.{CasbackTransactionStats, OfferStats}
import doobie._
import javax.inject.{Inject, Singleton}

import scala.language.implicitConversions

@Singleton
class StatsDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  // override useless fields
  override type T = Unit
  override val tableName = ""
  override val keyFieldList = Nil
  override val allFieldList = Nil

  def refreshTxns: IO[Int] = {
    val sql = "refresh materialized view cashback_transaction_stats"
    Update[Unit](sql).toUpdate0(()).run
  }


  def getOfferStats: IO[Option[OfferStats]] = {
    val query = "select count(offer_id) from merchant_offers"
    Query[Unit, OfferStats](query, None).toQuery0(()).option
  }

  def getTransactionStats(dsr: DashboardStatsRequest, customer: Option[String]): IO[Option[CasbackTransactionStats]] = {
    val query = Fragment.const(
      """select
        |       coalesce(sum(transactions_count),0),
        |       coalesce(sum(purchase_amount_sum),0),
        |       coalesce(sum(cashback_base_usd_sum),0),
        |       coalesce(sum(cashback_amount_usd_sum),0),
        |       coalesce(sum(cashback_user_usd_sum),0),
        |       coalesce(sum(cashback_own_usd_sum),0)
        |from cashback_transaction_stats t
      """).stripMargin

    val fmerc   = dsr.merchant.map { s => Fragment.const(s"merchant_name ilike '$s%' OR merchant_name ilike '% $s%'") }
    val fstatus = dsr.status.map   { s => Fragment.const(s"status = '${s.entryName}'") }
    val fbegin  = dsr.beginsAt.map { s => Fragment.const(s"date >= '$s'") }
    val fend    = dsr.endsAt.map   { s => Fragment.const(s"date <= '$s'") }
    val fcust   = customer.map     { s => Fragment.const(s"customer_name = '$s'") }

    val fcats   = dsr.tags.map     { s =>
      val catArray = "'{" + s.mkString(",") + "}'::text[]"
      Fragment.const(s"$catArray && categories")
    }

    val where = Fragments.whereAndOpt(fcust, fmerc, fstatus, fbegin, fend, fcats)

    val result = query ++ where

    result.query[CasbackTransactionStats].option
  }
}
