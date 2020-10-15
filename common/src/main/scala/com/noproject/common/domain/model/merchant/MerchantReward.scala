package com.noproject.common.domain.model.merchant

import com.noproject.common.domain.model.Money

case class MerchantReward(
  bestOffer:      MerchantRewardItem
, featured:       List[MerchantRewardItem] = Nil
, rewardLimit:    Option[Money] = None
, rewardCurrency: Option[String] = None
)

