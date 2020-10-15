package com.noproject.common.domain.codec

import com.noproject.common.data.ElementDiff
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTransactionResponse}
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import com.noproject.common.codec.json.ElementaryCodecs._

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
object DomainCodecsFactory {

  // cashback txn codecs
  private[codec] val (_txnEnc, _txnDec, _txnDiff) = {
    val enc  = Encoder[CashbackTransaction]
    val dec  = Decoder[CashbackTransaction]
    val diff = ElementDiff[CashbackTransaction]
    (enc, dec, diff)
  }

  // txn response codecs
  private[codec] val (_txnRespEnc, _txnRespDec) = {
    val enc  = Encoder[CashbackTransactionResponse]
    val dec  = Decoder[CashbackTransactionResponse]
    (enc, dec)
  }

  // event log item codecs
  private[codec] val (_eliEnc, _eliDec, _eliDiff) = {
    val enc  = Encoder[EventLogItem]
    val dec  = Decoder[EventLogItem]
    val diff = ElementDiff[EventLogItem]
    (enc, dec, diff)
  }

}



