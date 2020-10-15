package com.noproject.partner.button.domain.model

import com.noproject.common.domain.model.{Money, Percent}
import com.noproject.common.domain.model.merchant.MerchantRewardItem

case class UsebuttonOffer(
  id:          String
, ratePercent: Option[BigDecimal]
, rateFixed:   Option[BigDecimal]
, category:    Option[String]
) {
  def asMerchantRewardItem(params: Option[Map[String, String]] = None): MerchantRewardItem = {
    MerchantRewardItem(rateFixed.map(Money(_)), ratePercent.map(Percent(_)), params)
  }
}
