package com.noproject.data.gen

import java.time.Clock

import cats.effect.IO
import com.noproject.common.cache.KeyValueCache
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}
import com.noproject.common.controller.dto.OfferSearchParams
import com.noproject.common.data.gen.{RandomValueGenerator, TestClock}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO, MerchantOfferDiffDAO}
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import com.noproject.common.domain.service.{ConsumerDataService, MerchantCacheService, MerchantDataService}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class WeightedConsumerGeneratorTest extends WordSpec
  with DefaultPersistenceTest
  with RandomValueGenerator
  with Matchers
  with BeforeAndAfterAll
  with MockFactory {

  private val clock = TestClock.apply
  private val mdao = new MerchantDAO(xar)
  private val odao = new MerchantOfferDAO(xar, clock)
  private val ndao = new NetworkDAO(xar)
  private val ddao = stub[MerchantOfferDiffDAO]

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  val (cache, shutdown) = KeyValueCache.apply[String, MerchantOfferRow](300, clock, None).allocated.unsafeRunSync()
  val mcs = new MerchantCacheService(odao, xar, cache)
  val mds = new MerchantDataService(mdao, odao, ddao, xar, StaticOffersConfigProvider, Clock.systemUTC())

  var gen: WeightedConsumerGenerator = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val offers = mds.findMerchantOffers(OfferSearchParams(), None).unsafeRunSync()
    gen = new WeightedConsumerGenerator(offers)
  }

  override protected def afterAll(): Unit = {
    shutdown.unsafeRunSync()
    super.afterAll()
  }

  "WeightedConsumerGeneratorTest" should {

    "genNext" in {
      val (cust, profile, txns) = gen.genNext("mastercard")

      println(s"Customer: ${cust}")
      println(s"Profile: ${profile}")
      println(s"Transactions: ${txns}")
    }

  }
}
