package com.noproject.partner.azigo.domain.model

import java.time.Instant

import com.noproject.common.codec.json.JsonEncoded
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.customer.{TransactionPrototype, WrappedTrackingParams}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.model.{CashbackUserId, Money}
import io.circe.Json

case class AzigoTxn (
  programName:                        String
, userEmail:                          Option[String]
, status:                             String
, uniqueRecordId:                     String
, suppliedSubProgramId:               Option[String]
, transactionId:                      Long
, storeOrderId:                       Option[String]
, userId:                             Option[String]
, suppliedUserId:                     Option[String] // must be like partnerId::userId
, storeName:                          Option[String]
, tentativeCannotChangeAfterDate:     Long
, tentativeCannotChangeAfterDatetime: Option[String]
, timestamp:                          Long
, datetime:                           Instant
, postDatetime:                       Instant
, sale:                               Double
, commission:                         Double
, userCommission:                     Double
, sourceType:                         String
, resellerPayoutDate:                 Option[Instant]
, logoUrl:                            Option[String]
, cashbackFailedReason:               Option[String]
, rawJson:                            Option[Json]
) extends TransactionPrototype {

  import com.noproject.common.domain.model.transaction.CashbackTxnStatus._

  override lazy val txnId: String = transactionId.toString

  override lazy val jsonEncoded: Json = rawJson.getOrElse {
    import io.circe.generic.auto._
    import io.circe.syntax._
    this.asJson
  }

  override def getEncodedParams: Option[String] = suppliedUserId

  def asCashbackTransaction(tp: WrappedTrackingParams, now: Instant, mms: MerchantMappings): CashbackTransaction = {
    val totalCb = Money(this.commission)
    val userCb  = Money(this.userCommission)
    val ourCb   = totalCb - userCb

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
    , reference         = uniqueRecordId // String
    , merchantName      = mms.remapName(storeName.getOrElse("Unknown")) // String
    , merchantNetwork   = "azigo"// String
    , description       = Some(programName) // Option[String]
    , whenCreated       = datetime // Instant
    , whenUpdated       = postDatetime // Instant
    , whenClaimed       = None // Option[Instant]
    , whenSettled       = whenSettled // Option[Instant]
    , whenPosted        = Some(postDatetime) // Option[Instant]
    , purchaseDate      = this.datetime// Instant
    , purchaseAmount    = Money(this.sale)// BigDecimal
    , purchaseCurrency  = "USD" // String
    , cashbackBaseUSD   = Money(this.sale) // BigDecimal
    , cashbackTotalUSD  = totalCb // BigDecimal
    , cashbackUserUSD   = userCb // BigDecimal
    , cashbackOwnUSD    = ourCb // BigDecimal
    , status            = status // CashbackTxnStatus
    , parentTxn         = None // Option[FUUID]
    , payoutId          = None // Option[String]
    , failedReason      = None // Option[String]
    , rawTxn            = rawJson.getOrElse(Json.Null) // Json
    , offerId           = tp.offer // Json
    , offerTimestamp    = tp.time // Json
    )

  }

}
