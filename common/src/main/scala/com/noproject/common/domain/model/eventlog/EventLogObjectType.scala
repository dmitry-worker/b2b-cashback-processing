package com.noproject.common.domain.model.eventlog

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait EventLogObjectType extends EnumEntry with Snakecase
object EventLogObjectType extends Enum[EventLogObjectType] {
  case object CashbackTxn extends EventLogObjectType
  case object Merchant extends EventLogObjectType
  case object UsebuttonTxn extends EventLogObjectType
  // ...and other event types

  override def values = findValues
}

