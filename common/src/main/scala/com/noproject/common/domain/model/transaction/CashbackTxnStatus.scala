package com.noproject.common.domain.model.transaction

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait CashbackTxnStatus extends EnumEntry with Snakecase
object CashbackTxnStatus extends Enum[CashbackTxnStatus] {
  case object Pending     extends CashbackTxnStatus
  case object Available   extends CashbackTxnStatus
  case object Paid        extends CashbackTxnStatus
  case object Rejected    extends CashbackTxnStatus
  case object Expired     extends CashbackTxnStatus
  override def values = findValues
}
