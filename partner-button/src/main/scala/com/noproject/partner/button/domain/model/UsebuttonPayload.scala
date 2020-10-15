package com.noproject.partner.button.domain.model

import java.time.Instant

import com.noproject.common.codec.json.JsonEncoded
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.customer.{TransactionPrototype, WrappedTrackingParams}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.model.transaction.CashbackTxnStatus.{Available, Pending, Rejected}
import io.circe.Json

case class UsebuttonPayload(
  order_currency:         Option[String]
, modified_date:          Instant
, created_date:           Instant
, order_line_items:       Option[List[UsebuttonOrderItem]]
, button_id:              Option[String]
, campaign_id:            Option[String]
, rate_card_id:           Option[String]
, order_id:               Option[String]
, customer_order_id:      Option[String]
, account_id:             String
, btn_ref:                Option[String]
, currency:               String
, pub_ref:                Option[String]
, status:                 String
, event_date:             Option[Instant]
, order_total:            UsebuttonCents
, advertising_id:         Option[String]
, publisher_organization: String
, commerce_organization:  String
, amount:                 UsebuttonCents
, button_order_id:        Option[String]
, publisher_customer_id:  Option[String]
, id:                     String
, order_click_channel:    Option[String]
, category:               String
, validated_date:         Option[Instant]
, rawJson:                Option[Json]
) extends JsonEncoded with TransactionPrototype {

  private val DEFAULT_USER_COMISSION_PERCENT = 10

  override def txnId: String = id

  lazy val jsonEncoded: Json = {
    val existing = rawJson.flatMap { json =>
      json.hcursor.downField("data").focus
    }
    existing.getOrElse {
      import io.circe.generic.auto._
      import io.circe.syntax._
      this.asJson
    }
  }

  override def getEncodedParams: Option[String] = publisher_customer_id

  override def asCashbackTransaction(tp: WrappedTrackingParams, now: Instant, mms: MerchantMappings): CashbackTransaction = {
    val totalCb = this.amount.toMoney
    val userCb  = (totalCb * DEFAULT_USER_COMISSION_PERCENT / 100)
    val ourCb = totalCb - userCb

    val status = this.status.toLowerCase match {
      case "pending" => Pending
      case "user"    => Pending
      case "locked"  => Available
      case _         => Rejected
    }
    val whenSettled = status match {
      case Pending => None
      case _       => Some(Instant.now())
    }

    CashbackTransaction(
        id                = RandomValueGenerator.randomUUID
      , userId            = tp.user.userId
      , customerName      = tp.user.customerName
      , reference         = this.id // String
      , merchantName      = this.commerce_organization // String
      , merchantNetwork   = "usebutton"// String
      , description       = Some(this.commerce_organization) // Option[String]
      , whenCreated       = this.created_date // Instant
      , whenUpdated       = this.modified_date // Instant
      , whenClaimed       = None // Option[Instant]
      , whenSettled       = whenSettled // Option[Instant]
      , whenPosted        = None // Option[Instant]
      , purchaseDate      = this.event_date.getOrElse(this.created_date) // Instant
      , purchaseAmount    = this.order_total.toMoney // BigDecimal
      , purchaseCurrency  = "USD" // String
      , cashbackBaseUSD   = this.order_total.toMoney // BigDecimal
      , cashbackTotalUSD  = totalCb // BigDecimal
      , cashbackUserUSD   = userCb // BigDecimal
      , cashbackOwnUSD    = ourCb // BigDecimal
      , status            = status // CashbackTxnStatus
      , parentTxn         = None // Option[FUUID]
      , payoutId          = None // Option[String]
      , failedReason      = None // Option[String]
      , rawTxn            = rawJson.getOrElse(Json.Null) // Json
      , offerId           = tp.offer // Option[String]
      , offerTimestamp    = tp.time // Option[Instant]
    )
  }
}