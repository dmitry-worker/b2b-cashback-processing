package com.noproject.common.domain.codec

import com.noproject.common.data.ElementDiff
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTransactionResponse, CashbackTxnStatus}
import io.circe.{Decoder, Encoder, Json}

/**
  * * To boost compile time we want to create a codec once
  * * Instead of importing circe macros everywhere.
  *
  * We have separated codec definition and implicit value for a reason:
  * Although it is possible to declare:
  * `implicit val encoder:Encoder[T] = Encoder[T]`
  * Scala compiler will consider is a circular implicit
  * And therefore will ignore imported generic.auto._ to create it
  */
trait DomainCodecs {

  import DomainCodecsFactory._

  implicit val txnEnc: Encoder[CashbackTransaction] = _txnEnc
  implicit val txnDec: Decoder[CashbackTransaction] = _txnDec
  implicit val txnDiff: ElementDiff[CashbackTransaction] = _txnDiff

  implicit val txnRespEnc: Encoder[CashbackTransactionResponse] = _txnRespEnc
  implicit val txnRespDec: Decoder[CashbackTransactionResponse] = _txnRespDec

  implicit val eliEnc: Encoder[EventLogItem] = _eliEnc
  implicit val eliDec: Decoder[EventLogItem] = _eliDec
  implicit val eliDiff: ElementDiff[EventLogItem] = _eliDiff

}


object DomainCodecs extends DomainCodecs
