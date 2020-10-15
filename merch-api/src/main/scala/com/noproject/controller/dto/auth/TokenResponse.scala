package com.noproject.controller.dto.auth

import java.time.Instant

case class TokenResponse(
  token: String
, expiredAt: Instant
)
