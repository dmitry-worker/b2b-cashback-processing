package com.noproject.service.crap

import java.time.Instant

import cats.effect.IO
import com.noproject.common.domain.dao.CashbackTransactionDAO
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO}
import com.noproject.common.data.gen.{RandomValueGenerator, TestClock}
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, MerchantRewardItem, MerchantRow}
import com.noproject.common.domain.model.{Location, Money}
import com.noproject.common.domain.dao.merchant.MerchantOfferDAO
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import com.noproject.domain.dao.crap.CrapDAO
import com.noproject.domain.model.merchant._
import io.circe.Json
import javax.inject.{Inject, Singleton}

import scala.math.BigDecimal.RoundingMode
import scala.util.Random

@Singleton
class CrapService @Inject()(txnDAO: CashbackTransactionDAO
                          , crapDAO: CrapDAO
                          , mDAO: MerchantDAO
                          , oDAO: MerchantOfferDAO) extends RandomValueGenerator {
  private val generatedPrefix = "_generated_"

  private val clock = TestClock.apply

  def genTxns(count: Int, customer: Option[String], network: Option[String], from: Option[Instant], to: Option[Instant]): IO[Int] = {
    for {
      mercs <- mDAO.findAll
      txns   = createTxns(count, mercs, customer, network, from, to)
      res   <- txnDAO.upsert(txns, clock.instant() )
    } yield res
  }

  def deleteTxns(): IO[Int] = {
    crapDAO.deleteCrapTxns(generatedPrefix)
  }

  def genMerchants(count: Int): IO[Int] = {
    mDAO.insert(createMerchants(count))
  }

  def deleteMerchants(): IO[Int] = {
    crapDAO.deleteCrapMerchants(generatedPrefix)
  }

  def genOffers(count: Int, network: Option[String]): IO[Int] = {
    for {
      mercs <- mDAO.findAll
      offs   = createOffers(count, mercs, network)
      res   <- oDAO.insert(offs)
    } yield res
  }

  def deleteOffers(): IO[Int] = {
    crapDAO.deleteCrapOffers(generatedPrefix)
  }

  private def createMerchants(count: Int): List[MerchantRow] = {
    (1 to count).map { _ =>
      MerchantRow(
        merchantName = randomString
      , description  = generatedPrefix
      , logoUrl      = randomString
      , imageUrl     = None
      , categories   = List(generatedPrefix).map(_.toLowerCase)
      , priceRange   = None
      , website      = None
      , phone        = None
      )
    }.toList
  }

  private def createOffers(count: Int, mercs: List[MerchantRow], network: Option[String] = None): List[MerchantOfferRow] = {
    (1 to count).map { _ =>
      val now = Instant.now.minusSeconds(randomInt(60*60*24))
      val merc: MerchantRow = mercs(Random.nextInt(mercs.length))
      val location = Location(randomDouble * 360, randomDouble * 180 - 90)
      MerchantOfferRow(
          offerId = randomStringUUID
        , offerDescription = generatedPrefix
        , offerLocation = randomOptBoolean.map(_ => location)
        , offerAddress = randomOptString
        , merchantName = merc.merchantName
        , merchantNetwork = network.getOrElse(generatedPrefix).toLowerCase
        , tocText = randomOptString
        , tocUrl = randomOptString
        , images = randomOf("img1" :: "img2" :: "img3" :: "img4" :: "img5" :: Nil).toList
        , whenUpdated = now
        , whenModified = now
        , whenActivated = now
        , whenDeactivated = None
        , requiresActivation = randomBoolean
        , requiresBrowserCookies = randomBoolean
        , requiresBankLink = randomBoolean
        , requiresCardLink = randomBoolean
        , requiresGeoTracking = randomBoolean
        , requiresExclusive = randomBoolean
        , requiresExperimental = randomBoolean
        , rewardFixedBest = randomMoney(10)
        , rewardPercentBest = randomPercent(10)
        , rewardLimit = randomOptMoney(1000)
        , rewardCurrency = randomOptString
        , rewardItems = MerchantRewardItem(Some(Money(10)), None, Some(Map("key" -> "value"))) :: Nil
        , acceptedCards = randomOf("card1" :: "card2" :: "card3" :: Nil).toList
        , trackingRule = randomOptString
        , offerRawSrc = Json.fromFields(List("fieldName" -> Json.fromString("fieldValue")))
      )
    }.toList
  }

  private def createTxns(count: Int, mercs: List[MerchantRow],
                         customer: Option[String] = None,
                         network: Option[String] = None,
                         from: Option[Instant] = None,
                         to: Option[Instant] = None): List[CashbackTransaction] = {
    import scala.concurrent.duration._

    val now = Instant.now
    val end = to.getOrElse(now)
    val begin = from.getOrElse(end.minusMillis(1.day.toMillis))
    val statuses = CashbackTxnStatus.values

    (1 to count).map { _ =>
      val merc = mercs(randomInt(mercs.length))
      val time = randomInstantInRange(begin, end)

      val amount = randomMoney(1000)
      CashbackTransaction(
          id = randomUUID
        , userId = randomString
        , customerName = customer.getOrElse(randomString)
        , reference = randomStringUUID
        , merchantName = merc.merchantName
        , merchantNetwork = network.getOrElse(generatedPrefix).toLowerCase
        , description = Some(generatedPrefix)
        , whenCreated = now
        , whenUpdated = now
        , whenClaimed = None
        , whenSettled = None
        , whenPosted = None
        , purchaseDate = time
        , purchaseAmount = amount
        , purchaseCurrency = "USD"
        , cashbackBaseUSD = amount
        , cashbackTotalUSD = amount * 0.10
        , cashbackUserUSD = amount * 0.03
        , cashbackOwnUSD = amount * 0.07
        , status = statuses(randomInt(statuses.length))
        , parentTxn = None
        , payoutId = None
        , failedReason = None
        , rawTxn = Json.fromFields(Nil)
        , offerId = randomOptString
        , offerTimestamp = Some(now)
      )
    }.toList
  }
}
