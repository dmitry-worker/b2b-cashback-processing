package com.noproject.config

case class AuthConfig (
  // made optional, enabled when empty,
  // config test is failed otherwise
  key: String
, expirationMinutes: Int
, sessionCacheSeconds: Int
)

