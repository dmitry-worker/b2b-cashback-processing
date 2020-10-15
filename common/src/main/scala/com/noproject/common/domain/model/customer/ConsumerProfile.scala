package com.noproject.common.domain.model.customer

import java.time.{Instant, LocalDateTime}

case class ConsumerProfile(
  hash: String
, name: Option[String] = None
, age: Option[Int] = None
, male: Option[Boolean] = None
, phone: Option[String] = None
, address: Option[String] = None
, incomeClass: Option[Int] = None
, purchasesCount: Int = 0
, purchasesAmount: BigDecimal = 0
, lastPurchaseDate: Option[Instant] = None
, lastPurchaseUsdAmount: Option[BigDecimal] = None
)

object ConsumerProfile {

  def instance(hash: String): ConsumerProfile = {
    ConsumerProfile(hash)
  }

}