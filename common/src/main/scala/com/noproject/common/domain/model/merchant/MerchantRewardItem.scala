package com.noproject.common.domain.model.merchant

import com.noproject.common.domain.model.{Money, Percent}

import scala.math.BigDecimal.RoundingMode

case class MerchantRewardItem(
  rewardFixed:        Option[Money] = None
, rewardPercent:      Option[Percent] = None
, params:             Option[Map[String, String]] = None
) {

  def share(percentage: Int): MerchantRewardItem = {
    val pct = Percent(percentage)
    copy(
      rewardFixed   = this.rewardFixed.map(x => x.percent(pct))
    , rewardPercent = this.rewardPercent.map(x => x.percent(pct))
    )
  }

  def apply(purchase: Money): Money = {
    (rewardFixed, rewardPercent) match {
      case (Some(fix), _) => fix
      case (_, Some(pct)) => pct.apply(purchase)
      case _              => throw new RuntimeException("Cannot happen")
    }
  }

}

object MerchantRewardItem {

  def fixed[T: Numeric](name: String, amt: T): MerchantRewardItem = {
    val decimal = implicitly[Numeric[T]].toDouble(amt)
    val rew = Money(decimal)
    MerchantRewardItem(rewardFixed = Some(rew))
  }

  def percent[T: Numeric](name: String, amt: T): MerchantRewardItem = {
    val decimal = implicitly[Numeric[T]].toDouble(amt)
    val rew = Percent(decimal)
    MerchantRewardItem(rewardPercent = Some(rew))
  }

}