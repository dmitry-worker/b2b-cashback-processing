package com.noproject.common.domain.model.merchant

import java.time.Instant

import cats.kernel.Eq
import com.noproject.common.domain.model.{GinWrapper, Location, Money, Percent}
import io.circe.Json

case class MerchantOfferRow(
  offerId:                String
, offerDescription:       String
, offerLocation:          Option[Location]
, offerAddress:           Option[String]
, merchantName:           String
, merchantNetwork:        String
, tocText:                Option[String]
, tocUrl:                 Option[String]
, images:                 List[String]
, whenUpdated:            Instant
, whenModified:           Instant
, whenActivated:          Instant
, whenDeactivated:        Option[Instant]
, requiresActivation:     Boolean
, requiresBrowserCookies: Boolean
, requiresBankLink:       Boolean
, requiresCardLink:       Boolean
, requiresGeoTracking:    Boolean
, requiresExclusive:      Boolean
, requiresExperimental:   Boolean
, rewardFixedBest:        Money
, rewardPercentBest:      Percent
, rewardLimit:            Option[Money]
, rewardCurrency:         Option[String]
, rewardItems:            List[MerchantRewardItem]
, acceptedCards:          List[String]
, trackingRule:           Option[String]
, offerRawSrc:            Json
) extends GinWrapper {

  override def searchIndex: String = List(merchantName, offerDescription).mkString(". ")

  def extern(baseUrl: String, customer: String): MerchantOffer = {

    val requirements = MerchantRequirements(
      activation     = this.requiresActivation
    , browserCookies = this.requiresBrowserCookies
    , bankLink       = this.requiresBankLink
    , cardLink       = this.requiresCardLink
    , geoTracking    = this.requiresGeoTracking
    , experimental   = this.requiresExperimental
    , exclusiveOffer = this.requiresExclusive
    )

    val reward = MerchantReward(
      bestOffer = MerchantRewardItem(None, Some(rewardPercentBest))
    , featured = rewardItems
    , rewardLimit = this.rewardLimit
    , rewardCurrency = this.rewardCurrency
    )

    MerchantOffer(
      offerId          = this.offerId
    , offerDescription = this.offerDescription
    , offerLocation    = this.offerLocation
    , offerAddress     = this.offerAddress
    , tocText          = this.tocText
    , tocUrl           = this.tocUrl
    , images           = this.images
    , whenActivated    = this.whenActivated
    , whenDeactivated  = this.whenDeactivated
    , requirements     = requirements
    , reward           = reward
    , acceptedCards    = this.acceptedCards
    , network          = this.merchantNetwork
    , isOnline         = this.trackingRule.isDefined
    , trackingUrl      = this.trackingRule.map(_ => baseUrl + "?offer=" + offerId + "&customer=" + customer)
    )

  }

}

object MerchantOfferRow {

  implicit val eq = Eq.instance[MerchantOfferRow] { (f,s) =>

    (
       f.offerId == s.offerId
    && f.offerDescription == s.offerDescription
    && f.offerLocation == s.offerLocation
    && f.offerAddress == s.offerAddress
    && f.merchantName == s.merchantName
    && f.merchantNetwork == s.merchantNetwork
    && f.tocText == s.tocText
    && f.tocUrl == s.tocUrl
    && f.images == s.images
    && f.whenUpdated == s.whenUpdated
    && f.whenModified == s.whenModified
    && f.whenActivated == s.whenActivated
    && f.whenDeactivated == s.whenDeactivated
    && f.requiresActivation == s.requiresActivation
    && f.requiresBrowserCookies == s.requiresBrowserCookies
    && f.requiresBankLink == s.requiresBankLink
    && f.requiresCardLink == s.requiresCardLink
    && f.requiresGeoTracking == s.requiresGeoTracking
    && f.requiresExclusive == s.requiresExclusive
    && f.requiresExperimental == s.requiresExperimental
    && f.rewardFixedBest == s.rewardFixedBest
    && f.rewardPercentBest == s.rewardPercentBest
    && f.rewardLimit == s.rewardLimit
    && f.rewardCurrency == s.rewardCurrency
    && f.rewardItems == s.rewardItems
    && f.acceptedCards == s.acceptedCards
    && f.trackingRule == s.trackingRule
    )
  }

}