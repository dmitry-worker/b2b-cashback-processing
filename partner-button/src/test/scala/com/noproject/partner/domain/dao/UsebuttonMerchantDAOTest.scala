package com.noproject.partner.domain.dao

import java.time.Instant

import com.noproject.common.data.gen.Generator
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.model.{Money, Percent}
import com.noproject.common.domain.model.merchant.MerchantRewardItem
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.partner.button.domain.dao.UsebuttonMerchantDAO
import com.noproject.partner.button.domain.model.{UsebuttonMerchantRow, UsebuttonMerchantUrls, UsebuttonRewardItem}
import io.circe.Json
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import doobie.implicits._


class UsebuttonMerchantDAOTest extends WordSpec with DefaultPersistenceTest with Matchers with BeforeAndAfterAll {

  val dao = new UsebuttonMerchantDAO(xar)

  val mercs: List[UsebuttonMerchantRow] = (0 until 10).map(_ => genMerc).toList

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    dao.deleteAll().unsafeRunSync()
  }


  override protected def afterAll(): Unit = {
    dao.deleteAll().unsafeRunSync()
    super.afterAll()
  }

  "UsebuttonMerchantDAOTest" should {
    "insert and select mercs" in {
      val cio = for {
        i <- dao.insertTxn(mercs)
        r <- dao.findAllTxn
      } yield (i, r)

      val (i, res) = cio.transact(rollbackTransactor).unsafeRunSync

      i shouldBe mercs.size
      res.toSet shouldEqual mercs.toSet
    }

    "parse featured" in {
      val obj = Map(
        "VOD rental" -> UsebuttonRewardItem(percent = Some(2.8))
      , "for movie ticket purchase" -> UsebuttonRewardItem(fixed = Some(0.07))
      , "gift card or fanshop purchase" -> UsebuttonRewardItem(percent = Some(2.1))
      )
      val objJson = obj.asJson
      val rsp = 80
      val merc = genMerc.copy(
        featured = objJson
      , revenueSharePercent = rsp
      )

      val cio = for {
        _ <- dao.insertTxn(List(merc))
        d <- dao.findAllTxn
      } yield d

      val mercDb = cio.transact(rollbackTransactor).unsafeRunSync().head

      val params = Some(Map(
        "merchantId"  -> mercDb.name
      , "website"   -> mercDb.homepageUrl
      ))

      val rewardItems = List(
        MerchantRewardItem(rewardPercent = Some(Percent(2.8)), params = params).share(rsp)
      , MerchantRewardItem(rewardFixed = Some(Money(0.07)), params = params).share(rsp)
      , MerchantRewardItem(rewardPercent = Some(Percent(2.1)), params = params).share(rsp)
      )

      val offer = mercDb.asOffer(MerchantMappings(Map.empty, Map.empty), Instant.now)
      offer.rewardItems.toSet shouldEqual rewardItems.toSet
    }
  }

  implicit def genJson: Generator[Json] = new Generator[Json] {
    override def apply: Json = Json.fromValues(Nil)
  }
  def genMerc: UsebuttonMerchantRow = Generator[UsebuttonMerchantRow].apply

}
