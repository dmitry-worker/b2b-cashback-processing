package com.noproject.data.gen

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.noproject.common.data.gen.RandomValueGenerator._
import com.noproject.common.domain.model.Money
import com.noproject.common.domain.model.customer.{Consumer, ConsumerProfile, ProfilePattern}
import com.noproject.common.domain.model.merchant.{Merchant, MerchantOffer}
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import io.circe.Json


class WeightedConsumerGenerator(merchants: Seq[Merchant]) {


  // map Category -> List[merchant]
  private val merchantMap = merchants.foldLeft(Map[String, List[Merchant]]()) { (r, m) =>
    m.categories.foldLeft(r) { (res, cat) =>
      val result = res.get(cat) match {
        case Some(seq) => m :: seq
        case _         => m :: Nil
      }
      res + (cat -> result)
    }
  }

  println(merchantMap.keys)

  // sample categories from azigo
//  {accessories,"art & music",clothing,"department stores","gifts & flowers","health & wellness"}
//  {education,entertainment,misc.,services}
//  {accessories,clothing,entertainment}
//  {"art & music",education,entertainment,"gifts & flowers"}
//  {"gifts & flowers","health & wellness","home & garden"}
//  {"department stores","food & dining","health & wellness","home & garden"}
//  {accessories,clothing,office,"sports & recreation"}
//  {"department stores",electronics,"games & toys"}
//  {accessories,auto,"department stores",electronics,"games & toys","home & garden"}
//  {accessories,auto,"department stores",electronics,"games & toys","home & garden",office,"sports & recreation"}
//  {accessories,clothing,"department stores","gifts & flowers",office,"sports & recreation"}
//  {accessories,clothing,"sports & recreation"}
//  {"department stores","food & dining","gifts & flowers"}
//  {education,entertainment,"games & toys",kids,travel}
//  {electronics,"home & garden",misc.}
//  {accessories,"gifts & flowers"}
//  {education,"games & toys",services}
//  {"department stores","gifts & flowers"}
//  {clothing,"department stores",entertainment,"health & wellness",kids,"sports & recreation"}
//  {entertainment,"health & wellness","home & garden",kids}
//  {clothing,"department stores",entertainment,"gifts & flowers","home & garden","sports & recreation"}
//  {education,office,services}
//  {"deal sites",entertainment}
//  {"art & music",entertainment,misc.,services,"sports & recreation"}
//  {clothing,"department stores",education,entertainment,"gifts & flowers","health & wellness"}
//  {"department stores","food & dining","gifts & flowers","home & garden",office}
//  {"department stores","gifts & flowers","health & wellness","home & garden",travel}
//  {"health & wellness"}
//  {accessories,"art & music",clothing,"home & garden"}
//  {accessories,clothing,"home & garden",office,"sports & recreation"}
//  {accessories,clothing,kids,office,"sports & recreation"}
//  {accessories,"deal sites","home & garden"}
//  {accessories,"art & music",clothing,"department stores","gifts & flowers"}
//  {clothing,"gifts & flowers","health & wellness","home & garden"}
//  {"gifts & flowers","health & wellness",travel}
//  {education,entertainment,"games & toys","gifts & flowers",misc.}
//  {entertainment,"health & wellness",pets}
//  {"art & music",clothing,"department stores","home & garden"}
//  {"art & music",misc.}
//  {accessories,clothing,"gifts & flowers","home & garden"}
//  {accessories,"gifts & flowers",kids}
//  {"department stores",entertainment,"home & garden",misc.,pets}
//  {clothing,"sports & recreation"}
//  {accessories,"art & music",clothing,"department stores"}
//  {entertainment,"gifts & flowers",office,services}
//  {"art & music",entertainment,"home & garden"}
//  {"art & music",entertainment,"gifts & flowers","home & garden"}
//  {accessories,clothing,"department stores","sports & recreation"}
//  {beauty,"home & garden","sports & recreation"}
//  {"department stores",entertainment,"gifts & flowers","home & garden",kids}
//  {accessories,clothing,"food & dining","games & toys"}
//  {clothing,education,entertainment,"games & toys",kids}
//  {clothing,"department stores",office,"sports & recreation"}
//  {accessories,clothing,"department stores","health & wellness"}
//  {accessories,clothing,"department stores","gifts & flowers"}
//  {"food & dining","gifts & flowers","sports & recreation"}
//  {"home & garden",misc.,office,services}
//  {clothing,"department stores","gifts & flowers","health & wellness"}
//  {clothing,"department stores",entertainment,"games & toys","home & garden",kids}
//  {education,entertainment,services}
//  {accessories,clothing,"deal sites",entertainment}
//  {clothing,"department stores","gifts & flowers","health & wellness","sports & recreation"}
//  {beauty,clothing,electronics,"home & garden"}
//  {clothing,entertainment,"home & garden"}
//  {auto,travel}
//  {clothing,"department stores",misc.,"sports & recreation"}
//  {clothing,"deal sites","gifts & flowers"}
//  {education,kids}
//  {entertainment,"gifts & flowers"}
//  {"deal sites","gifts & flowers","home & garden"}
//  {"department stores",entertainment,"food & dining","gifts & flowers","home & garden"}
//  {"art & music",entertainment,misc.,travel}
//  {education}
//  {beauty,clothing,electronics,"food & dining","gifts & flowers","health & wellness","home & garden",services,travel}

  val profilePatterns = List(
    ProfilePattern( // young and rich
      true
    , Range(18,35)
    , Range(5,10)
    , Map(
        "auto" -> 0.3
      , "sports & recreation" -> 0.2
      , "art & music" -> 0.10
      , "travel" -> 0.2
      , "food & dining" -> 0.10
      )
    )
  , ProfilePattern(  // young and rich and beautiful
      false
    , Range(18,35)
    , Range(5,10)
    , Map(
        "health & wellness" -> 0.3
      , "gifts & flowers" -> 0.3
      )
    )
  , ProfilePattern( // young and poor
      true
    , Range(18,35)
    , Range(1,5)
    , Map(
        "clothing" -> 0.3
      , "education" -> 0.2
      , "electronics" -> 0.18
      , "food & dining" -> 0.10
      )
    )
  , ProfilePattern(  // young and poor and beautiful
      false
    , Range(18,35)
    , Range(1,5)
    , Map(
        "clothing" -> 0.3
      , "education" -> 0.2
      , "health & wellness" -> 0.18
      , "food & dining" -> 0.15
      )
    )


    // OLD
  , ProfilePattern( // old and rich
      true
    , Range(35,70)
    , Range(5,10)
    , Map(
        "auto" -> 0.10
      , "art & music" -> 0.25
      , "travel" -> 0.3
      , "food & dining" -> 0.10
      , "department stores" -> 0.05
      , "home & garden" -> 0.10
      )
    )
  , ProfilePattern(  // old and rich and beautiful
      false
    , Range(35, 70)
    , Range(5,10)
    , Map(
        "art & music" -> 0.18
      , "travel" -> 0.35
      , "food & dining" -> 0.20
      , "home & garden" -> 0.15
      )
    )
  , ProfilePattern( // old and poor
      true
    , Range(35,70)
    , Range(1,5)
    , Map(
        "clothing" -> 0.15
      , "food & dining" -> 0.1
      , "home & garden" -> 0.35
      , "electronics" -> 0.15
      )
    )
  , ProfilePattern(  // old and poor and beautiful
      false
    , Range(35,70)
    , Range(1,5)
    , Map(
        "clothing" -> 0.3
      , "department stores" -> 0.18
      , "food & dining" -> 0.15
      , "home & garden" -> 0.25
      )
    )
//, ProfilePattern(...)
  )


  def genNext(customerName: String): (Consumer, ConsumerProfile, List[CashbackTransaction]) = {

    val consumer = Consumer(customerName, randomStringUUID)

    // this parameter has impact on qty and amount of transactions
    val incomeClass = randomIntRange(1, 10)

    val profile = ConsumerProfile(
      hash = consumer.hash
    , name = randomOptBoolean.map(_ => randomStringLen(10))
    , age = Some(randomIntRange(20, 70))
    , male = Some(randomBoolean)
    , phone = Some(randomLongInRange(1000000000L, 9999999999L).toString)
    , address = randomOptString
    , incomeClass = Some(incomeClass)
    , purchasesCount = 0
    , purchasesAmount = 0
    , lastPurchaseDate = None
    , lastPurchaseUsdAmount = None
    )

    val txnCount = 1 + randomInt(3 * (incomeClass + 1))
    val txns = (0 until txnCount).map { _ =>

      val maybePattern = profilePatterns.find(_.matches(profile))
      if (maybePattern.isEmpty) {
        println(s"No pattern for profile ${profile}")
      }
      val pattern  = maybePattern.get
      val category = randomOneOf(pattern.weightedMap)
      val merchant = randomOneOf(merchantMap(category))
      val offer    = randomOneOf(merchant.offers)
      val reward   = offer.reward.bestOffer

      val day  = 1000 * 60 * 60 * 24
      val withinMonth = Instant.now()
        .minus(randomInt(180), ChronoUnit.DAYS)
        .plusMillis(randomInt(day).toLong)
        .toEpochMilli
      val now = Instant.ofEpochMilli(withinMonth)

      val statuses = CashbackTxnStatus.values

      val amount = randomMoney((10000 * (incomeClass + 1).toDouble / 5).toInt)
      val cashback = reward(amount)

      CashbackTransaction(
        id               = randomUUID
      , userId           = consumer.hash
      , customerName     = customerName
      , reference        = randomStringUUID
      , merchantName     = merchant.merchantName
      , merchantNetwork  = offer.network
      , description      = Some(offer.offerDescription)
      , whenCreated      = now
      , whenUpdated      = now
      , whenClaimed      = None
      , whenSettled      = None
      , whenPosted       = None
      , purchaseDate     = now
      , purchaseAmount   = amount
      , purchaseCurrency = "USD"
      , cashbackBaseUSD  = amount
      , cashbackTotalUSD = cashback
      , cashbackUserUSD  = cashback
      , cashbackOwnUSD   = Money.zero
      , status           = statuses(randomInt(statuses.length))
      , parentTxn        = None
      , payoutId         = None
      , failedReason     = None
      , rawTxn           = Json.fromFields(Nil)
      , offerId          = Some(offer.offerId)
      , offerTimestamp   = Some(offer.whenActivated)
      )

    }

    val pcount = txns.length
    val pamt = txns.map(_.purchaseAmount.amount).sum
    val last = txns.maxBy(_.whenCreated)

    val finalProfile = profile.copy(
      purchasesCount = pcount
    , purchasesAmount = pamt
    , lastPurchaseDate = Some(last.whenCreated)
    , lastPurchaseUsdAmount = Some(last.purchaseAmount.amount)
    )

    (consumer, finalProfile, txns.toList)

  }

}
