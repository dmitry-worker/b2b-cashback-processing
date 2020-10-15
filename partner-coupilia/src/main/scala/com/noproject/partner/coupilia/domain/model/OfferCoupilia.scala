package com.noproject.partner.coupilia.domain.model

import java.time.{Instant, LocalDate, LocalDateTime}

import com.noproject.common.Decimals
import com.noproject.common.domain.model.{Location, Money, Percent}
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, MerchantReward, MerchantRewardItem}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.partner.coupilia.domain.model.CoupiliaCommissionType.{FlatRate, Percentage}
import io.circe.Json

case class OfferCoupilia(
  id:              Long
, code:            Option[String]
, merchant:        String
, network:         Option[String]
, website:         Option[String]
, logo:            Option[String]
, url:             List[MerchantCoupiliaUrl]
, dealtype:        Option[String]
, keywords:        Option[String]
, restrictions:    Option[String]
, offer:           String
, grouponcities:   Option[List[String]]
, mobilecertified: Boolean
, country:         String
, lastupdated:     Option[LocalDateTime]
, enddate:         LocalDate
, startdate:       LocalDate
, imagefileurl:    Option[String]
, merchantid:      String
, categories:      List[MerchantCoupiliaCategory]
, lat:             Option[Double]
, lon:             Option[Double]
, networkid:       Option[String]
, rating:          Int
, holiday:         Option[String]
, trackingpixel:   Option[String]
, shiptocountries: Option[String]
) {

  def isActive(now: LocalDate): Boolean = {
    startdate.isBefore(now) && enddate.minusDays(1).isAfter(now)
  }


  def loc: Option[Location] = for {
    latitude  <- lat
    longitude <- lon
  } yield Location(longitude, latitude)


  def asOffer(mc: MerchantCoupilia, mappings: MerchantMappings, moment: Instant): MerchantOfferRow = {

    val (rewFixed, rewPercent) = mc.commissiontype match {
      case Percentage => None -> Some(Percent.apply(mc.commission))
      case FlatRate   => Some(Money.apply(mc.commission)) -> None
    }

    val reward = MerchantReward(
      bestOffer       = MerchantRewardItem(rewFixed, rewPercent)
    , featured        = Nil
    , rewardLimit     = None
    , rewardCurrency  = Some(mc.commissioncurrency.toUpperCase)
    )

    val name   = mappings.remapName(merchant)
    val desc   = offer
    val images = List(imagefileurl).flatten

    MerchantOfferRow(
      offerId                = id.toString // String
    , offerDescription       = desc // String
    , offerLocation          = None // Option[Location]
    , offerAddress           = None // Option[String]
    , merchantName           = name // String
    , merchantNetwork        = "coupilia" // String
    , tocText                = restrictions // Option[String]
    , tocUrl                 = None // Option[String]
    , images                 = images // List[String]
    , whenUpdated            = moment // Instant
    , whenModified           = moment // Instant
    , whenActivated          = moment // Instant
    , whenDeactivated        = None // Option[Instant]
    , requiresActivation     = false // Boolean
    , requiresBrowserCookies = false // Boolean
    , requiresBankLink       = false // Boolean
    , requiresCardLink       = false // Boolean
    , requiresGeoTracking    = false // Boolean
    , requiresExclusive      = false // Boolean
    , requiresExperimental   = false // Boolean
    , rewardFixedBest        = reward.bestOffer.rewardFixed.getOrElse(Money.zero) // BigDecimal
    , rewardPercentBest      = reward.bestOffer.rewardPercent.getOrElse(Percent.zero) // BigDecimal
    , rewardLimit            = reward.rewardLimit // Option[BigDecimal]
    , rewardCurrency         = reward.rewardCurrency // Option[String]
    , rewardItems            = reward.featured // List[MerchantRewardItem]
    , acceptedCards          = Nil // List[String]
    , trackingRule           = Some(url.head.location) // Option[String]
    , offerRawSrc            = Json.fromFields(Nil) // Json
    )
  }


}
