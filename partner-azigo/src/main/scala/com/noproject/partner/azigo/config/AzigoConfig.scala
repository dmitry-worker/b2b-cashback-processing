package com.noproject.partner.azigo.config

// value classes
case class AzigoConfig(
  affiliate: AzigoAffiliateConfig
, sso:       AzigoSsoConfig
)

case class AzigoAffiliateConfig(
  api:    String
, secret: String
)

case class AzigoSsoConfig(
  url:    String
, secret: String
)