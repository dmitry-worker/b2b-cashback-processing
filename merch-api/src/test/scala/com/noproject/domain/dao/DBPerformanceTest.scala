package com.noproject.domain.dao

import cats.effect.IO
import cats.implicits._
import com.noproject.common.controller.dto.OfferSearchParams
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.merchant.MerchantOfferDAO
import com.noproject.common.data.gen.{RandomValueGenerator, TestClock}
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import org.scalatest.{DoNotDiscover, Matchers, WordSpec}


@DoNotDiscover
class DbPerformanceTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers {

  val clock = TestClock.apply

  val dao = new MerchantOfferDAO(xar, clock)

  "Stress test" should {

    "parallel search " in {
      val iolist: List[IO[List[MerchantOfferRow]]] = (0 to 1000).map { _ =>
        dao.find(OfferSearchParams(search = Some(randomString)))
      }.toList
      val res = iolist.parSequence.unsafeRunSync()
      println(res.length)
    }

  }

}