package com.noproject.partner.button.domain.model

case class UsebuttonMerchantExclusiveOffer(
  id:             String
, ratePercent:    Option[BigDecimal]
, rateFixed:      Option[BigDecimal]
, displayParams:  Option[Map[String, String]]
)
