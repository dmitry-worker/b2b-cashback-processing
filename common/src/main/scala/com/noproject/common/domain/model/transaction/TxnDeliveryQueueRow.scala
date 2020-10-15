package com.noproject.common.domain.model.transaction

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID

case class TxnDeliveryQueueRow(
  txnId:              FUUID
, customerName:       String
, whenCreated:        Instant
, whenNextAttempt:    Instant
, whenLastAttempt:    Option[Instant]
, lastAttemptOutcome: Option[String]
, attemptCount:       Int
, batchId:            Option[FUUID]
) {

  require( !whenCreated.isAfter(whenNextAttempt) )
  require( whenLastAttempt.forall(!_.isAfter(whenNextAttempt)) )
  require( whenLastAttempt.forall(!_.isBefore(whenCreated)) )

}

object TxnDeliveryQueueRow {

  def fromTxn(ct: CashbackTransaction, now: Instant): TxnDeliveryQueueRow = {
    TxnDeliveryQueueRow(
      txnId              = ct.id
    , customerName       = ct.customerName
    , whenCreated        = now
    , whenNextAttempt    = now
    , whenLastAttempt    = None
    , lastAttemptOutcome = None
    , attemptCount       = 0
    , batchId            = None
    )
  }

}