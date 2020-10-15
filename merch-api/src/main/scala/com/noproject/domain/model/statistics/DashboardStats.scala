package com.noproject.domain.model.statistics


case class CasbackTransactionStats(
  transactionsCount: Long = 0
, purchaseAmount: BigDecimal = 0
, cashbackBaseAmount: BigDecimal = 0
, cashbackTotalAmount: BigDecimal = 0
, cashbackUserAmount: BigDecimal = 0
, cashbackOwnAmount: BigDecimal = 0
)

// more fields will be added in future
case class OfferStats(offersCount: Long = 0)

case class DashboardStats(
  offerStats: OfferStats
, transactionStats: CasbackTransactionStats
)
