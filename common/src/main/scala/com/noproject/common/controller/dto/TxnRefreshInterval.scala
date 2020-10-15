package com.noproject.common.controller.dto

import java.time.{Clock, Instant}

case class TxnRefreshInterval(
  from:     Instant
, to:       Instant
)

object TxnRefreshInterval {
  val defaultDelta = 60 * 60 * 24

  def apply(optFrom: Option[Instant], optTo: Option[Instant])(implicit clock: Clock): TxnRefreshInterval = {
    val now = clock.instant
    val to = optTo.getOrElse(now)
    val from = optFrom.getOrElse(to.minusSeconds(defaultDelta))
    new TxnRefreshInterval(from, to)
  }
}


