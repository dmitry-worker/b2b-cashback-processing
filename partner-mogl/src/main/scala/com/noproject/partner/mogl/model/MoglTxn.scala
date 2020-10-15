package com.noproject.partner.mogl.model

import java.time.Instant

case class MoglTxnRedemption(
  id:             Long
, state:          String
, dateAdded:      Instant
, cashbackAmount: Double
, referralFee:    Double
, qualifiedSpend: Double
, offerId:        Long
)


case class MoglTxnUpdate(
  `type`:      Option[MoglTxnType]
, transaction: MoglTxn
)

case class MoglTxn(
  id:                   Long
, userId:               Option[Long]
, amount:               Double
, cashbackAmount:       Double
, cashbackBilled:       Double
, referralFee:          Double
, dateOfTransaction:    Instant
, rewardTime:           Instant
, dateProcessed:        Instant
, venue:                MoglMerchantShort
, user:                 MoglUserInfo
, cardId:               Long
, clearingAmount:       Option[Double]
, authorizationAmount:  Double
, last4:                String
, redemptions:          List[MoglTxnRedemption]
, cashbackFailedReason: Option[String]) {

//  lazy val cardIdStr = cardId.toString
//  lazy val idStr = id.toString

}
