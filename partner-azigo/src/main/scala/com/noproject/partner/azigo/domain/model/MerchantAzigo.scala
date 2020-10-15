package com.noproject.domain.model.merchant.azigo

import java.time.Instant

import com.noproject.common.Decimals
import com.noproject.common.domain.model.{Money, Percent}
import com.noproject.common.domain.model.Money._
import com.noproject.common.domain.model.merchant._
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import io.circe.syntax._
import org.http4s.Uri
import io.circe.generic.auto._

case class AzigoMerchantOffer(
  snippet:          String
, description:      String
, offerLink:        String
)


case class AzigoMerchantCommission(
  categoryName:     String,
  userCategoryPct:  Double,
  categoryPct:      Double
)


case class MerchantAzigo(
  userPct:                 Double
, storeLink:               String
, storeName:               String
, storeGuid:               String
, cookieless:              String
, phrase:                  String
, description:             String
, commissionIsDollars:     Int
, giftcardExcluded:        Int
//, commissionCategories:    List[AzigoMerchantCommission]
, categories:              List[String]
, offers:                  List[AzigoMerchantOffer]
, logoUrl:                 String
, commissionCategoryCount: Option[Int]
, commissionCategoryMax:   Option[Double]
, userCommissionOffer:     String
, userCommissionPct:       Double
, grossCommissionPct:      Double
) extends MerchantItem {

  val name             = storeName
  val qualifiedNetwork = "azigo"
  val placeholder  = "_PLACEHOLDER_"
  val requirements = MerchantRequirements(browserCookies = this.cookieless != "Y")

  override def asMerchant(mappings: MerchantMappings): MerchantRow = {
    val newName = mappings.remapName(this.storeName)
    MerchantRow(
      merchantName = newName
    , description  = this.description
    , logoUrl      = this.logoUrl
    , imageUrl     = Some(this.logoUrl)
    , categories   = this.categories.map(_.toLowerCase)
    , priceRange   = None
    , website      = Some(this.storeLink)
    , phone        = None
    )
  }

  override def asOffer(mappings: MerchantMappings, moment: Instant): MerchantOfferRow = {

    val reward = MerchantReward(
      bestOffer = MerchantRewardItem(
        rewardFixed        = None
      , rewardPercent      = Some(Percent(userCommissionPct))
      )
    , featured = Nil
    , rewardLimit = None
    , rewardCurrency = None
    )

    val trackingRule = {
      val src = Uri
        .unsafeFromString(storeLink)
        .withQueryParam("u", placeholder)
      src.toString.replace(placeholder, "{userId}")
    }

    val newName = mappings.remapName(this.storeName)

    MerchantOfferRow(
      offerId                = this.storeGuid // String
    , offerDescription       = this.phrase // String
    , offerLocation          = None // Option[Location]
    , offerAddress           = None // Option[String]
    , merchantName           = newName // String
    , merchantNetwork        = "azigo" // String
    , tocText                = Some(this.description) // Option[String]
    , tocUrl                 = None // Option[String]
    , images                 = Nil // List[String]
    , whenUpdated            = moment // Instant
    , whenModified           = moment // Instant
    , whenActivated          = moment // Instant
    , whenDeactivated        = None // Option[Instant]
    , requiresActivation     = false // Boolean
    , requiresBrowserCookies = this.cookieless != "Y" // Boolean
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
    , trackingRule           = Some(trackingRule) // Option[String]
    , offerRawSrc            = this.asJson // Json
    )
  }

}
