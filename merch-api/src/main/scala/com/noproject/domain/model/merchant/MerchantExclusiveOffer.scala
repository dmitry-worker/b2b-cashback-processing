package com.noproject.domain.model.merchant

import java.time.Instant

import com.noproject.common.domain.model.merchant.{Merchant, MerchantRewardItem}


case class MerchantExclusiveOffer(
  userId:           Long
, merchantName:     String
, merchantNetwork:  String
, received:         Instant
, offers:           Map[String, MerchantRewardItem]
, bestOfferId:      Option[String]
) {

  @transient lazy val key = (userId, merchantName, merchantNetwork)

  def apply(mir: Merchant): Merchant = {
//    val newReward = bestOfferId match {
//      case Some(best) =>
//        MerchantReward(
//          bestOffer = offers(best)
//        , featured = (offers - best).values.toList
//        , rewardLimit = mir.reward.rewardLimit
//        , rewardCurrency = mir.reward.rewardCurrency
//        )
//      case _ =>
//        mir.reward
//    }
//    mir.copy(
//      reward   = newReward
//    )
    ???
  }

  def bestOffer: Option[MerchantRewardItem] = {
    bestOfferId.flatMap(best => offers.get(best))
  }

}
