package com.noproject.partner.button.domain.model

case class UsebuttonMerchantExclusive(
  merchantId:  String
, bestOfferId: String
, offers:      Seq[UsebuttonMerchantExclusiveOffer]
)


