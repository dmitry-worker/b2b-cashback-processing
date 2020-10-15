package com.noproject.common.domain.model.merchant

import java.time.Instant

import io.circe.Json

case class MerchantOfferDiff(
  offerId:   String
, timestamp: Instant
, diff:      Json
)