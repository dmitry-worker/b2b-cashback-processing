package com.noproject.common.stream

import cats.effect.IO
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.model.transaction.CashbackTransaction
import dev.profunktor.fs2rabbit.model.{AckResult, DeliveryTag}

object RabbitOps {

  type TransactionCallback = (CashbackTransaction, DeliveryTag) => IO[AckResult]
  type EventCallback = (EventLogItem, DeliveryTag) => IO[AckResult]

}
