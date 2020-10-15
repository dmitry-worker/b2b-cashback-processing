package com.noproject.partner.coupilia.domain.model

import java.time.{Instant, LocalDateTime, ZoneOffset}

import com.noproject.common.Decimals
import com.noproject.common.codec.json.JsonEncoded
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.{CashbackUserId, Money}
import com.noproject.common.domain.model.customer.{TransactionPrototype, WrappedTrackingParams}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import io.circe.Json
import com.noproject.common.domain.model.Money._

case class CoupiliaTxn(
  id:                   String
, orderid:              Long
, network:              String
, eventdate:            LocalDateTime
, advertisername:       String
, subaffiliateid:       Option[String]
, commissionid:         Long
, commissionamount:     BigDecimal
, networkstatus:        String
, saleamount:           BigDecimal
, commonUserId:          Option[String]
, cashbackFailedReason: Option[String]
, rawJson:              Option[Json]
) extends JsonEncoded with TransactionPrototype {

  override def txnId: String = id

  override lazy val jsonEncoded: Json = rawJson.getOrElse {
    import io.circe.generic.auto._
    import io.circe.syntax._
    import CoupiliaCodec._
    this.asJson
  }

  override def getEncodedParams: Option[String] = subaffiliateid

  override def asCashbackTransaction(tp: WrappedTrackingParams, now: Instant, mms: MerchantMappings): CashbackTransaction = {
    val totalCb = Money(this.commissionamount)
    val userCb  = Money(this.commissionamount)
    val ourCb = Money.zero

    val name = mms.nameMappings.getOrElse(advertisername, advertisername)
    val eventInstant = this.eventdate.toInstant(ZoneOffset.UTC)

    val status = CashbackTxnStatus.Available
    val whenSettled = Some(eventInstant)

    CashbackTransaction(
      id                = RandomValueGenerator.randomUUID
    , userId            = tp.user.userId
    , customerName      = tp.user.customerName
    , reference         = id // String
    , merchantName      = name // String
    , merchantNetwork   = "coupilia"// String
    , description       = Some(advertisername) // Option[String]
    , whenCreated       = eventInstant // Instant
    , whenUpdated       = now // Instant
    , whenClaimed       = None // Option[Instant]
    , whenSettled       = whenSettled // Option[Instant]
    , whenPosted        = Some(eventInstant) // Option[Instant]
    , purchaseDate      = eventInstant// Instant
    , purchaseAmount    = Money(saleamount)// .round2// BigDecimal
    , purchaseCurrency  = "USD" // String
    , cashbackBaseUSD   = Money(saleamount) // BigDecimal
    , cashbackTotalUSD  = totalCb // BigDecimal
    , cashbackUserUSD   = userCb // BigDecimal
    , cashbackOwnUSD    = ourCb // BigDecimal
    , status            = status // CashbackTxnStatus
    , parentTxn         = None // Option[FUUID]
    , payoutId          = None // Option[String]
    , failedReason      = None // Option[String]
    , rawTxn            = this.rawJson.getOrElse(Json.Null) // Json
    , offerId           = tp.offer // Json
    , offerTimestamp    = tp.time // Json
    )

  }

}
