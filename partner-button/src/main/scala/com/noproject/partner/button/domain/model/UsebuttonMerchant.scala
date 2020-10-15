package com.noproject.partner.button.domain.model

import java.time.Instant

import com.noproject.common.Decimals
import com.noproject.common.domain.model.{Money, Percent}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.merchant._
import io.circe.Json

case class UsebuttonMerchant(
  id:                  String
, name:                String
, categories:          List[String]
, urls:                UsebuttonMerchantUrls
, metadata:            Map[String, String]
, availablePlatforms:  List[String] = Nil
, supportedProducts:   List[String] = Nil
, status:              String
, offers:              List[UsebuttonOffer]
, bestOfferId:         Option[String] = None
, offerLink:           String
, revenueSharePercent: Int
, exclusiveOffer:      Boolean = false
, termsAndConditions:  Option[String] = None
, deactivationDate:    Option[Instant] = None
) extends MerchantItem {

  lazy val qualifiedNetwork = "usebutton"

  lazy val requirements = MerchantRequirements(
    exclusiveOffer = this.exclusiveOffer
  )

  override def asMerchant(mappings: MerchantMappings): MerchantRow = {
    val newName = mappings.remapName(this.name)
    MerchantRow(
      merchantName = newName
      , description  = metadata.getOrElse("description", this.name)
      , logoUrl      = metadata.getOrElse("icon_url", "")
      , imageUrl     = metadata.get("icon_url")
      , categories   = categories.map(_.toLowerCase)
      , priceRange   = None
      , website      = Some(urls.homepage)
      , phone        = None
    )
  }

  override def asOffer(mappings: MerchantMappings, moment: Instant): MerchantOfferRow = {
    val bestOffer   = bestOfferId.flatMap(id => offers.find(_.id == id))
    val otherOffers = bestOfferId.map(id => offers.filterNot(_.id == id)).getOrElse(Nil)

    val reward = MerchantReward(
      bestOffer = bestOffer
        .map(_.asMerchantRewardItem())
        .getOrElse(MerchantRewardItem())
      , featured = otherOffers.map { o =>
        MerchantRewardItem(
          o.rateFixed.map( Money(_) )
        , o.ratePercent.map( Percent(_) )
        )
      }
      , rewardLimit = None
      , rewardCurrency = Some("USD")
    )

    val homepage  = Option(urls.homepage).getOrElse("")
    val iconUrl   = metadata.get("icon_url")
    val bannerUrl = metadata.get("banner_url")
    val newName   = mappings.remapName(this.name)
    val desc      = metadata.getOrElse("description", newName)
    val images    = List(iconUrl, bannerUrl).flatten

    MerchantOfferRow(
      offerId                = id // String
      , offerDescription       = desc // String
      , offerLocation          = None // Option[Location]
      , offerAddress           = None // Option[String]
      , merchantName           = newName // String
      , merchantNetwork        = "usebutton" // String
      , tocText                = metadata.get("description") // Option[String]
      , tocUrl                 = urls.terms_and_conditions // Option[String]
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
      , rewardFixedBest        = reward.bestOffer.rewardFixed.getOrElse(Money.zero) // Money
      , rewardPercentBest      = reward.bestOffer.rewardPercent.getOrElse(Percent.zero) // Money
      , rewardLimit            = reward.rewardLimit // Option[BigDecimal]
      , rewardCurrency         = reward.rewardCurrency // Option[String]
      , rewardItems            = reward.featured // List[MerchantRewardItem]
      , acceptedCards          = Nil // List[String]
      , trackingRule           = Some(offerLink) // Option[String]
      , offerRawSrc            = Json.fromFields(Nil) // Json
    )
  }

//  /*
//  Partly update because some fields was set manually
//  */
//  def partlyUpdate(muResponseItem: UsebuttonMerchantResponseItem): UsebuttonMerchant =
//    UsebuttonMerchant(
//        id                  = muResponseItem.id
//      , name                = muResponseItem.name
//      , categories          = muResponseItem.categories
//      , metadata            = muResponseItem.metadata
//      , urls                = muResponseItem.urls
//      , status              = muResponseItem.status
//      , revenueSharePercent = 0
//      , exclusiveOffer      = this.exclusiveOffer
//      , featured            = Map()
//    )

}

object UsebuttonMerchant {
  val NETWORK = "usebutton"
}
