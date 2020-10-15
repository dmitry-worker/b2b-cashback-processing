package com.noproject.common.domain.service

import java.time.{Clock, Instant}

import cats.effect.IO
import cats.kernel.Eq
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}
import com.noproject.common.data.DataUnordered
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO, MerchantOfferDiffDAO}
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.domain.model.{Location, Money}
import com.noproject.common.domain.model.merchant.{MerchantOfferDiff, MerchantOfferRow, MerchantRewardItem, MerchantRow}
import com.noproject.common.domain.model.partner.Network
import doobie.{HC, Transactor}
import doobie.implicits._
import io.circe.Json
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import io.circe.generic.auto._
import com.noproject.common.codec.json.ElementaryCodecs._

class OfferDiffTest extends WordSpec with BeforeAndAfterAll with DefaultPersistenceTest with RandomValueGenerator with Matchers {

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  val clock = TestClock.apply
  val ndao = new NetworkDAO(xar)
  val mdao = new MerchantDAO(xar)
  val modao = new MerchantOfferDAO(xar, clock)
  val modiffdao = new MerchantOfferDiffDAO(xar)
  val mds = new MerchantDataService(mdao, modao, modiffdao, xar, StaticOffersConfigProvider, clock)

  implicit val eq = Eq.fromUniversalEquals[MerchantOfferRow]

  override def beforeAll(): Unit = {
    super.beforeAll()
    val io = for {
      _ <- ndao.deleteAll()
      _ <- mdao.deleteAll()
      _ <- modao.deleteAll()
      _ <- modiffdao.deleteAll()
    } yield ()
    io.unsafeRunSync()
  }

  "OfferDiffTest" should {
    "insert diffs and restore origin offer" in {
      val network = Network("azigo", None)
      val merc = genMerc

      val offerOrigin  = genOffer(merc.merchantName, network.name)
      val offerChange1 = offerOrigin.copy(
        offerAddress = randomOptString,
        images = List(randomString, randomString, randomString)
      )
      val offerChange2 = offerChange1.copy(
        offerDescription = randomString,
        rewardItems = List(MerchantRewardItem(randomOptMoney(100), randomOptPct(100), None))
      )

      val du0 = DataUnordered[String, MerchantOfferRow](List(offerOrigin), _.offerId, Set())
      val du1 = DataUnordered[String, MerchantOfferRow](List(offerChange1), _.offerId, Set())
      val du2 = DataUnordered[String, MerchantOfferRow](List(offerChange2), _.offerId, Set())
      val chSet1 = du0 /|\ du1
      val chSet2 = du1 /|\ du2

      val diff1 = chSet1.update.values.head.diff.get
      val diff2 = chSet2.update.values.head.diff.get

      val now = clock.instant()

      val rio = for {
        _ <- ndao.insertTxn(List(network))
        _ <- mdao.insertTxn(List(merc))
        _ <- modiffdao.insert(MerchantOfferDiff(offerOrigin.offerId, now.minusSeconds(10), diff1))
        _ <- modiffdao.insert(MerchantOfferDiff(offerOrigin.offerId, now.minusSeconds(5), diff2))
        _ <- modao.insertTxn(List(offerChange2))
        r <- mds.restoreOfferTxn(offerOrigin.offerId, now)
      } yield r

      val restored = rio.transact(rollbackTransactor).unsafeRunSync()

      restored shouldEqual Some(offerOrigin)
    }
  }

  def genOffer(name: String, net: String): MerchantOfferRow = {
    val location = Location(randomDouble * 360, randomDouble * 180 - 90)

    implicit val genRewardItem: Generator[MerchantRewardItem] = new Generator[MerchantRewardItem] {
      override def apply: MerchantRewardItem = {
        MerchantRewardItem(Some(Money(10)), None, Some(Map("key" -> "value")))
      }
    }
    implicit val genLocation: Generator[Location] = new Generator[Location] {
      override def apply: Location = location
    }

    implicit val genJson: Generator[Json] = new Generator[Json] {
      override def apply: Json = Json.fromFields(List("fieldName" -> Json.fromString("fieldValue")))
    }

    implicit val genInstant: Generator[Instant] = new Generator[Instant] {
      override def apply: Instant = clock.instant()
    }

    Generator[MerchantOfferRow].apply.copy(
      merchantName = name,
      merchantNetwork = net,
    )
  }

  def genMerc: MerchantRow = Generator[MerchantRow].apply
}
