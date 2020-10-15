package com.noproject.partner.button.config

case class UsebuttonConfig(
  webhookSecret:     String
, apiKey:            String
, apiSecret:         String
, expirationMinutes: Int
, url:               String
, organizationId:    String
, accountId:         String
)
