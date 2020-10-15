package com.noproject.partner.mogl.model

case class MoglMerchantOffer(
  id:                 Long//26579,
, rewardType:         MoglMerchantRewardType//"PERCENT",
, rewardValue:        Double//7.0,
, rewardMax:          Double//250.0,
, finePrint:          String//"Offer valid from 02/20/2018. Offer does not apply during scheduled times. Excludes: Sat. Maximum individual reward 250 dollars.",
, requiresActivation: Boolean//false,
, basic:              Boolean//false,
, details:            MoglMerchantDetails
, excludedBySchedule: Option[Boolean]//true
)
