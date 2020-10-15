package com.noproject.partner.button.domain.model

import java.time.Instant

import com.noproject.common.Decimals
import com.noproject.common.domain.model.{Money, Percent}
import com.noproject.common.domain.model.merchant._
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.security.Hash
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

/*
class for manual Usebutton offers processing
 */

case class UsebuttonMerchantRow(
  id:                  String
, name:                String
, categories:          List[String]
, availablePlatforms:  List[String] = Nil
, supportedProducts:   List[String] = Nil
, status:              String
, cpiFixed:            Option[BigDecimal] = None
, cpaPercent:          Option[BigDecimal] = None
, cpaFixed:            Option[BigDecimal] = None
, featured:            Json //,Map[String, UsebuttonRewardItem]
, deactivationDate:    Option[Instant] = None
, revenueSharePercent: Int
, termsAndConditions:  Option[String] = None
, exclusiveOffer:      Boolean = false
, homepageUrl:         String
, tocUrl:              Option[String] = None
, iconUrl:             Option[String] = None
, bannerUrl:           Option[String] = None
, description:         Option[String] = None
) extends MerchantItem {

  lazy val qualifiedNetwork = "usebutton"

  lazy val requirements = MerchantRequirements(
    exclusiveOffer = this.exclusiveOffer
  )

  override def asMerchant(mappings: MerchantMappings): MerchantRow = {
    val newName = mappings.remapName(this.name)
    MerchantRow(
      merchantName = newName
    , description  = description.getOrElse("")
    , logoUrl      = iconUrl.getOrElse("")
    , imageUrl     = bannerUrl
    , categories   = categories.map(_.toLowerCase)
    , priceRange   = None
    , website      = Some(homepageUrl)
    , phone        = None
    )
  }

  override def asOffer(mappings: MerchantMappings, moment: Instant): MerchantOfferRow = {
    val params = Some(Map(
      "merchantId"  -> name
      , "website"   -> homepageUrl
    ))

    val featuredMap: Map[String, UsebuttonRewardItem] = featured.as[Map[String, UsebuttonRewardItem]] match {
      case Left(e)  => Map.empty
      case Right(v) => v
    }

    val reward = MerchantReward(
      bestOffer = MerchantRewardItem(
        rewardFixed = cpaFixed.map( Money(_) )
      , rewardPercent = cpaPercent.map( Percent(_) )
      , params = params
      ).share(revenueSharePercent)
      , featured = featuredMap.map { case (_, value) =>
        MerchantRewardItem(
          value.fixed.map( Money(_) )
        , value.percent.map( Percent(_) )
        , params = params
        ).share(revenueSharePercent)
      }.toList
      , rewardLimit = None
      , rewardCurrency = Some("USD")
    )

    val homepage = Option(homepageUrl).getOrElse("")
    val newName  = mappings.remapName(this.name)
    val desc     = description.getOrElse(newName)
    val images   = List(iconUrl, bannerUrl).flatten

    MerchantOfferRow(
        offerId                = "usebutton::"+Hash.md5.hex(newName) // String
      , offerDescription       = desc // String
      , offerLocation          = None // Option[Location]
      , offerAddress           = None // Option[String]
      , merchantName           = newName // String
      , merchantNetwork        = "usebutton" // String
      , tocText                = termsAndConditions // Option[String]
      , tocUrl                 = tocUrl // Option[String]
      , images                 = images // List[String]
      , whenUpdated            = moment // Instant
      , whenModified           = moment // Instant
      , whenActivated          = moment // Instant
      , whenDeactivated        = deactivationDate // Option[Instant]
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
      , trackingRule           = Some(homepage) // Option[String]
      , offerRawSrc            = this.asJson // Json
    )
  }

}
