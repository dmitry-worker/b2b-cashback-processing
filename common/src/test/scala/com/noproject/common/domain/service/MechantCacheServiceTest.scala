package com.noproject.common.domain.service

import java.time.{Clock, Instant}

import cats.effect.IO
import com.noproject.common.cache.KeyValueCache
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO, MerchantOfferDiffDAO}
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.domain.model.{Location, Money}
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, MerchantRewardItem, MerchantRow}
import com.noproject.common.domain.model.partner.Network
import io.circe.Json
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class MechantCacheServiceTest extends WordSpec with BeforeAndAfterAll with DefaultPersistenceTest with RandomValueGenerator with Matchers with MockFactory {

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  val clock = TestClock.apply
  val mdao = new MerchantDAO(xar)
  val odao = new MerchantOfferDAO(xar, clock)
  val ndao = new NetworkDAO(xar)
  val ddao = stub[MerchantOfferDiffDAO]

  val (cache, shutdown) = KeyValueCache.apply[String, MerchantOfferRow](300, clock, None).allocated.unsafeRunSync()
  val mcs = new MerchantCacheService(odao, xar, cache)
  val mds = new MerchantDataService(mdao, odao, ddao, xar, StaticOffersConfigProvider, Clock.systemUTC())

  override def beforeAll(): Unit = {
    super.beforeAll()
    cleanup
  }

  override def afterAll(): Unit = {
    cleanup
    super.afterAll()
  }

  private def cleanup = {
    val io = for {
      _ <- mdao.deleteAll()
      _ <- odao.deleteAll()
      _ <- ndao.deleteAll()
    } yield ()
    io.unsafeRunSync()
  }

  "TrackingServiceTest" should {
    "insert and get from cache" in {
      val network = randomString
      val m1 = genMerc
      val o1 = genOffer.copy(merchantName = m1.merchantName, merchantNetwork = network)
      val o2 = genOffer.copy(merchantName = m1.merchantName, merchantNetwork = network)

      ndao.insert(List(Network(network, None))).unsafeRunSync()
      mds.insertMerchants(List(m1)).unsafeRunSync()
      mds.insertOffers(List(o1, o2)).unsafeRunSync()

      val res11 = mcs.findOfferRowById(o1.offerId).unsafeRunSync()
      res11 shouldEqual o1
      val res21 = mcs.findOfferRowById(o2.offerId).unsafeRunSync()
      res21 shouldEqual o2
      val res12 = mcs.findOfferRowById(o1.offerId).unsafeRunSync()
      res12 shouldEqual o1
      val res22 = mcs.findOfferRowById(o2.offerId).unsafeRunSync()
      res22 shouldEqual o2
    }

  }


  def genMerc: MerchantRow = Generator[MerchantRow].apply
  def genOffer: MerchantOfferRow = {
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
