package com.noproject.partner.mogl.model

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Uppercase

sealed trait MoglMerchantScheduleType extends EnumEntry with Uppercase
object MoglMerchantScheduleType extends Enum[MoglMerchantScheduleType] {
  case object Reward    extends MoglMerchantScheduleType
  case object Include   extends MoglMerchantScheduleType
  case object Exclude   extends MoglMerchantScheduleType
  override def values = findValues
}
