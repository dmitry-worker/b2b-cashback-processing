package com.noproject.partner.mogl.model

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Uppercase

sealed trait MoglMerchantRewardType extends EnumEntry with Uppercase
object MoglMerchantRewardType extends Enum[MoglMerchantRewardType] {
  case object Percent extends MoglMerchantRewardType
  override def values = findValues
}
