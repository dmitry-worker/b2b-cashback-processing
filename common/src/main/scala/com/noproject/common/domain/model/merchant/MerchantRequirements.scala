package com.noproject.common.domain.model.merchant

case class MerchantRequirements(
  activation:        Boolean = false
, browserCookies:    Boolean = false
, bankLink:          Boolean = false
, cardLink:          Boolean = false
, geoTracking:       Boolean = false
, experimental:      Boolean = false
, exclusiveOffer:    Boolean = false
)
