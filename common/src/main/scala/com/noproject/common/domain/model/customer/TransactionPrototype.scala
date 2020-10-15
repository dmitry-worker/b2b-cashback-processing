package com.noproject.common.domain.model.customer

import java.time.Instant

import com.noproject.common.codec.json.JsonEncoded
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.transaction.CashbackTransaction

trait TransactionPrototype extends JsonEncoded {

  def txnId: String

  def getEncodedParams: Option[String]

  def asCashbackTransaction(tp: WrappedTrackingParams, now: Instant, mms: MerchantMappings): CashbackTransaction

}
