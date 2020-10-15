package com.noproject.controller.dto.dashboard

import com.noproject.domain.model.statistics.{CasbackTransactionStats, OfferStats}

// more fields will be added in future
case class DashboardOffersStatsResponse(offers: Long)

object DashboardOffersStatsResponse {
  def apply(offers: OfferStats): DashboardOffersStatsResponse =
    new DashboardOffersStatsResponse(offers.offersCount)
}

case class DashboardItem[T](current: T, previous: T)

case class DashboardTransactionsStatsResponse(
  transactions: DashboardItem[Long]
, purchaseAmount: DashboardItem[BigDecimal]
, cashbackBaseAmount: DashboardItem[BigDecimal]
, cashbackTotalAmount: DashboardItem[BigDecimal]
, cashbackUserAmount: DashboardItem[BigDecimal]
, cashbackOwnAmount: DashboardItem[BigDecimal]
)

object DashboardTransactionsStatsResponse {
  def apply(curr: CasbackTransactionStats, prev: CasbackTransactionStats): DashboardTransactionsStatsResponse = {
    DashboardTransactionsStatsResponse(
      DashboardItem[Long](curr.transactionsCount, prev.transactionsCount),
      DashboardItem[BigDecimal](curr.purchaseAmount, prev.purchaseAmount),
      DashboardItem[BigDecimal](curr.cashbackBaseAmount, prev.cashbackBaseAmount),
      DashboardItem[BigDecimal](curr.cashbackTotalAmount, prev.cashbackTotalAmount),
      DashboardItem[BigDecimal](curr.cashbackUserAmount, prev.cashbackUserAmount),
      DashboardItem[BigDecimal](curr.cashbackOwnAmount, prev.cashbackOwnAmount)
    )
  }
}
