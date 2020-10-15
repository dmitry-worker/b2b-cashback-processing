package com.noproject.partner.button.domain.model

import com.noproject.common.security.Hash


case class UsebuttonOfferRequest(
  user_id:        String
, email_sha256s:  List[String]
, merchant_id:    Option[String]
)


object UsebuttonOfferRequest {
  val defaultUsebuttonUserId = "42"
  val defaultEmailSha256s = List(Hash.sha256.hex("usebutton_api_email@noproject.com"))

  def apply(merchantId: Option[String] = None): UsebuttonOfferRequest = UsebuttonOfferRequest(defaultUsebuttonUserId, defaultEmailSha256s, merchantId)
}


