package com.noproject.domain.model.customer

import java.time.Instant

case class Session(sessionId: Long, customerName: Option[String], sessionToken: String, startedAt: Instant, expiredAt: Instant)

object Session {
  def anonymous: Session = {
    val now = Instant.now
    Session(0, None, "", now, now)
  }
}

