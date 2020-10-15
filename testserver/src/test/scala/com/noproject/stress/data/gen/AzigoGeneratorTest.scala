package com.noproject.stress.data.gen

import java.time.Instant

import fs2.Stream
import com.noproject.common.data.gen.Generator
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.partner.azigo.domain.model.AzigoTxn
import io.circe.Json
import cats.effect.{IO, Resource}
import com.noproject.common.domain.model.{Location, Money}
import com.noproject.common.domain.model.customer.Consumer
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, MerchantRewardItem}
import com.noproject.stress.config.StressTestPartnerConfig
import org.scalatest.{Matchers, WordSpec}

import scala.math.BigDecimal.RoundingMode

class AzigoGeneratorTest extends WordSpec with Matchers with RandomValueGenerator {

  val sampleOffers = (0 until 10).toList.map(_ => genOffer)
  val sampleCustomers = (0 until 10).toList.map(_ => genConsumer)

  "It" should {

    "Generate a stream of azigo txns" in {
      val COUNT  = 1000
      val AMOUNT = COUNT * 100D
      val stpc = StressTestPartnerConfig("", COUNT, 0)
      val gen = new AzigoGenerator(stpc, sampleOffers, sampleCustomers, suiteId)
      val txns = gen.genStream.compile.toList.unsafeRunSync

      val totalSaleResult = txns.map(_.sale).sum
      val txnsCount       = txns.size

      round(totalSaleResult) shouldEqual round(AMOUNT)
      txnsCount shouldEqual COUNT
    }

  }

  private def round(d: Double) = BigDecimal(d).setScale(2, RoundingMode.HALF_UP)

  private def genConsumer: Consumer = ???

  private def genOffer: MerchantOfferRow = {
    val location = Location(randomDouble * 360, randomDouble * 180 - 90)

    implicit val genRewardItem:Generator[MerchantRewardItem] = new Generator[MerchantRewardItem] {
      override def apply: MerchantRewardItem = {
        MerchantRewardItem(Some(Money(10)), None, Some(Map("key" -> "value")))
      }
    }
    implicit val genLocation:Generator[Location] = new Generator[Location] {
      override def apply: Location = location
    }

    implicit val genJson:Generator[Json] = new Generator[Json] {
      override def apply: Json = Json.fromFields(List("fieldName" -> Json.fromString("fieldValue")))
    }

    implicit val genInstant:Generator[Instant] = new Generator[Instant] {
      override def apply: Instant = Instant.now
    }

    Generator[MerchantOfferRow].apply

  }



}
