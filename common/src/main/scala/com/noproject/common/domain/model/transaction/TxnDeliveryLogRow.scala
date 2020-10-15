package com.noproject.common.domain.model.transaction

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID
import io.circe.Json

case class TxnDeliveryLogRow(
  txnId:          FUUID
, batchId:        FUUID
, customerName:   String
, whenDelivered:  Instant
, diff:           Option[Json]
, failureReason:  Option[String] = None
)

case class TxnLastDelivery(txnId: FUUID, whenDelivered: Instant)