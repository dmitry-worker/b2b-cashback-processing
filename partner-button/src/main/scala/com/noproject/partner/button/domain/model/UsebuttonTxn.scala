package com.noproject.partner.button.domain.model

import java.time.Instant

import com.noproject.common.codec.json.JsonEncoded
import com.noproject.common.domain.model.customer.{TransactionPrototype, WrappedTrackingParams}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.transaction.CashbackTransaction
import io.circe.Json

case class UsebuttonTxn(
  request_id: String
, data:       UsebuttonPayload
, id:         String
, event_type: String
) extends JsonEncoded with UsebuttonCodecs with TransactionPrototype {

  override def txnId: String = data.id

  override def asCashbackTransaction(tp: WrappedTrackingParams, now: Instant, mms: MerchantMappings): CashbackTransaction = {
    data.asCashbackTransaction(tp, now, mms)
  }

  override def getEncodedParams: Option[String] = data.publisher_customer_id

  override def jsonEncoded: Json = {
    data.rawJson.getOrElse {
      import io.circe.generic.auto._
      import io.circe.syntax._
      this.asJson
    }
  }

}