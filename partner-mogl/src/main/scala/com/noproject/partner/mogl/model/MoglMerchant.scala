package com.noproject.partner.mogl.model

import java.time.Instant

import com.noproject.common.domain.model.{Location, Money, Percent}
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.merchant.{MerchantItem, MerchantOfferRow, MerchantRequirements, MerchantReward, MerchantRewardItem, MerchantRow}
import io.circe.Json

import scala.math.BigDecimal.RoundingMode

case class MerchantMogl(
  id:                 Long
, name:               String
, phone:              Option[String]
, website:            Option[String]
, address:            MoglAddress
, latitude:           Double
, longitude:          Double
, thumbnailUrl:       String
, rating:             Double
, menuUrl:            Option[String]
, priceRange:         Option[String]
, categories:         List[String]
, primaryCategory:    Option[String]
, description:        Option[String]
, buzz:               List[String]
, medias:             Json
, defaultDiscount:    Double
, offers:             List[MoglMerchantOffer]
, acceptedCards:      List[String]
, simpleHours:        Json
, timezoneOffset:     Int
, status:             String
, jsonRaw:            Option[Json] = None
) extends MerchantItem {

  val qualifiedNetwork = "mogl"

  def offerToReward(offer: MoglMerchantOffer): MerchantRewardItem = {
    MerchantRewardItem(
      rewardFixed   = None
    , rewardPercent = Some(Percent(offer.rewardValue))
    , params        = Some(Map("finePrint" -> offer.finePrint))
    )
  }

  def withRawJson(json: Json) = this.copy(jsonRaw = Some(json))

  val bestOffer = {
    if (offers.isEmpty) {
      throw new RuntimeException(s"No offers in mogl merchantId=${id} merchantName=${name}")
    } else if (offers.size == 1) {
      offers.head
    } else {
      offers.foldLeft(offers.head) { (ex, off) =>
        if (off.rewardValue > ex.rewardValue) off else ex
      }
    }
  }

  val requirements = MerchantRequirements(
    activation    = bestOffer.requiresActivation
    , cardLink      = true
    , geoTracking   = true
  )

  private val MOGL_TEST_IMG_URL = "https://d10ukqbetc2okm.cloudfront.net/"
  private val MOGL_INVALID_URL = "https://test.mogl.com:444/"
  private val MOGL_INVALID_LEN = MOGL_INVALID_URL.length

  def fixImageUrl(url: String): String = {
    if (url.startsWith(MOGL_INVALID_URL)) MOGL_TEST_IMG_URL + url.substring(MOGL_INVALID_LEN)
    else url
  }

  override def asMerchant(mms: MerchantMappings): MerchantRow = {
    val name = mms.remapName(this.name)
    MerchantRow(
      merchantName = name
    , description  = this.description.getOrElse("")
    , logoUrl      = this.thumbnailUrl
    , imageUrl     = None
    , categories   = this.categories.map(_.toLowerCase)
    , priceRange   = this.priceRange
    , website      = this.website
    , phone        = this.phone
    )
  }

  override def asOffer(mappings: MerchantMappings, moment: Instant): MerchantOfferRow = {

    import com.noproject.common.domain.model.Money._

    val otherOffers = offers.filter(_.id != bestOffer.id).map(offerToReward)

    val reward = MerchantReward(
      bestOffer       = offerToReward(bestOffer)
    , featured        = otherOffers
    , rewardLimit     = Some(Money(bestOffer.rewardMax))
    , rewardCurrency  = Some("USD")
    )

    val mappedName = mappings.remapName(this.name)

    MerchantOfferRow(
      offerId                = this.id.toString //String
    , offerDescription       = this.description.getOrElse("") //String
    , offerLocation          = Some(Location(this.longitude, this.latitude)) //Option[Location]
    , offerAddress           = this.address.streetAddress //Option[String]
    , merchantName           = mappedName //String
    , merchantNetwork        = "mogl" //String
    , tocText                = None //Option[String]
    , tocUrl                 = None //Option[String]
    , images                 = Nil //List[String]
    , whenUpdated            = moment //Instant
    , whenModified           = moment //Instant
    , whenActivated          = moment //Instant
    , whenDeactivated        = None //Option[Instant]
    , requiresActivation     = false //Boolean
    , requiresBrowserCookies = false //Boolean
    , requiresBankLink       = false //Boolean
    , requiresCardLink       = true //Boolean
    , requiresGeoTracking    = true //Boolean
    , requiresExclusive      = false //Boolean
    , requiresExperimental   = false //Boolean
    , rewardFixedBest        = reward.bestOffer.rewardFixed.getOrElse(Money.zero) //BigDecimal
    , rewardPercentBest      = reward.bestOffer.rewardPercent.getOrElse(Percent.zero) //BigDecimal
    , rewardLimit            = reward.rewardLimit //BigDecimal
    , rewardCurrency         = reward.rewardCurrency //Option[String]
    , rewardItems            = reward.featured // List[MerchantRewardItem]
    , acceptedCards          = this.acceptedCards.toList //List[String]
    , trackingRule           = None //Option[String]
    , offerRawSrc            = this.jsonRaw.getOrElse(Json.Null) //Json
    )
  }

}








