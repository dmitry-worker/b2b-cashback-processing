package com.noproject.partner.mogl.model

import com.noproject.partner.mogl.model.MoglMerchantRewardType.findValues
import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Uppercase
import io.circe.{Decoder, Encoder, Json}

sealed trait MoglMerchantRenewalIntervalType extends EnumEntry with Uppercase
object MoglMerchantRenewalIntervalType extends Enum[MoglMerchantRenewalIntervalType] {
  case object DAY   extends MoglMerchantRenewalIntervalType
  case object HOUR  extends MoglMerchantRenewalIntervalType
  case object WEEK  extends MoglMerchantRenewalIntervalType
  case object MONTH extends MoglMerchantRenewalIntervalType
  override def values = findValues
}
