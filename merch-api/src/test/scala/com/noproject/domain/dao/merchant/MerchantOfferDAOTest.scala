package com.noproject.domain.dao.merchant

import java.time.Instant

import cats.instances.unit
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO}
import com.noproject.common.domain.dao.partner.{CustomerNetworksDAO, NetworkDAO}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.domain.dao.{JsonConvertible, DefaultPersistenceTest}
import com.noproject.common.domain.model.{Location, Money}
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, MerchantRewardItem, MerchantRow}
import com.noproject.common.domain.model.partner.Network
import doobie._
import doobie.implicits._
import io.circe.Json
import io.circe.generic.auto._
import com.noproject.common.codec.json.ElementaryCodecs._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

// TODO: upgrade other tests
class MerchantOfferDAOTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers with BeforeAndAfterAll {

  val clock = TestClock.apply

  val dao     = new MerchantOfferDAO(xar, clock)
  val mercDao = new MerchantDAO(xar)
  val netDao  = new NetworkDAO(xar)
  val cnetDao = new CustomerNetworksDAO(xar)
  val custDao = new CustomerDAO(xar)

  val networkNames = "azigo" :: "mogl" :: "coupilia" :: Nil

  val now = clock.instant()
  val times = now.minusSeconds(3600) :: now :: now.plusSeconds(3600) :: Nil

  val offers  = (0 until 10).map(_ => genOffer(randomOneOf(networkNames))).toList
  val mercs   = genMercs(offers.map(_.merchantName))

  implicit val rewardItemsMeta:Meta[List[MerchantRewardItem]] = JsonConvertible[List[MerchantRewardItem]]

  override def beforeAll = {
    super.beforeAll()

    (for {
      _ <- dao.deleteAll()
    } yield unit).unsafeRunSync()
  }

  "MerchantOfferDAOTest" should {

    "insert and retrieve" in {
      val io = for {
        _ <- netDao.insertTxn(networkNames.map(Network.apply(_, None)))
        _ <- mercDao.insertTxn(mercs)
        i <- dao.insertTxn(offers)
        r <- dao.findAllTxn
      } yield (i, r)
      val (count, result) = io.transact(rollbackTransactor).unsafeRunSync()

      count shouldEqual offers.length
      result.toSet shouldEqual offers.toSet
    }

//    "getFreshness" in {
//      val freshness = dao.getFreshnessData.unsafeRunSync()
//      implicit val order = Order.fromLessThan[Instant]( (f,s) => f.isBefore(s) )
//      println(s"Freshness: ${freshness.contents.asJson.spaces4}")
//      val maxAzigo    = offers.collect { case o if o.merchantNetwork == "azigo" => o.whenUpdated }.maximumOption
//      val maxMogl     = offers.collect { case o if o.merchantNetwork == "mogl" => o.whenUpdated }.maximumOption
//      val maxCoupilia = offers.collect { case o if o.merchantNetwork == "coupilia" => o.whenUpdated }.maximumOption
//      freshness.contents.get("coupilia") shouldEqual maxCoupilia
//      freshness.contents.get("azigo") shouldEqual maxAzigo
//      freshness.contents.get("mogl") shouldEqual maxMogl
//    }
//
//    "find" in {
//      // default find
//      val io1 = dao.find(OfferSearchParams())
//      io1.unsafeRunSync().sortBy(_.merchantName) shouldEqual offers.sortBy(_.merchantName)
//
//      // using location
//      val offerWithLocation = offers.find(_.offerLocation.isDefined).foreach { o =>
//        val io2 = dao.find(OfferSearchParams(nearby = Some(Circle(o.offerLocation.get, 100))))
//        io2.unsafeRunSync().head shouldEqual o
//      }
//
//      // fff
//      val io3 = dao.find(OfferSearchParams(
//        search     = Some("asdf")
//      , nearby     = Some(Circle(Location(1,2), 3))
//      , limit      = Some(1)
//      , offset     = Some(2)
//      , names      = Some(SearchParamsRule(true, NonEmptyList("asdf", Nil)))
//      , tags       = Some(SearchParamsRule(true, NonEmptyList("asdf", Nil)))
//      , ids        = Some(SearchParamsRule(true, NonEmptyList("asdf", Nil)))
//      , networks   = Some(SearchParamsRule(true, NonEmptyList("asdf", Nil)))
//      ))
//      io3.unsafeRunSync().sortBy(_.merchantName) shouldBe empty
//    }
//
//    "update" in {
//      val head = offers.head
//      val imgs = List("one", "two", "three")
//      val copy = head.copy(images = imgs)
//      val io1 = dao.update(copy).unsafeRunSync()
//      val io2 = dao.find(OfferSearchParams().withNames(NonEmptyList(head.merchantName, Nil))).unsafeRunSync()
//      io2.head shouldEqual copy
//    }
//
//    "delete" in {
//      //only even offers are getting delete
//      val (del, rem) = (0 until 100).zip(offers).partition { case (n, off) => n % 2 == 0 }
//
//      val delNames   = del.map(_._2.merchantName).toList
//      val remNames   = rem.map(_._2.merchantName).toList
//
//      val io1 = dao.delete(NonEmptyList.fromListUnsafe(delNames))
//      io1.unsafeRunSync() shouldEqual delNames.size
//
//      val io2 = dao.find(OfferSearchParams().withNames(NonEmptyList.fromListUnsafe(delNames)))
//      io2.unsafeRunSync() shouldBe empty
//
//      val io3 = dao.find(OfferSearchParams().withNames(NonEmptyList.fromListUnsafe(remNames)))
//      io3.unsafeRunSync() should contain theSameElementsAs rem.map{ case (_, off) => off }
//    }
//
//    "find for customer" in {
//      val apikey = randomString
//      custDao.insert(Customer(randomString, apikey, randomString, Set(AccessRole.Noaccess), None, None)).unsafeRunSync()
//      val customer = custDao.getByKey(apikey).unsafeRunSync().get
//
//      val network1 = Network(randomString, None)
//      val network2 = Network(randomString, None)
//      netDao.insert(List(network1, network2)).unsafeRunSync()
//      cnetDao.insert(customer.name, NonEmptyList(network1.name, Nil)).unsafeRunSync()
//
//      val offers1  = (0 until 10).map(_ => genOffer(network1.name)).toList
//      val offers2  = (0 until 10).map(_ => genOffer(network2.name)).toList
//
//      val merchants1 = genMercs(offers1.map(_.merchantName))
//      val merchants2 = genMercs(offers2.map(_.merchantName))
//      mercDao.insert(merchants1 ++ merchants2).unsafeRunSync()
//      dao.insert(offers1 ++ offers2).unsafeRunSync()
//
//      val res = dao.find(OfferSearchParams(), Some(customer.name)).unsafeRunSync()
//      res.toSet shouldEqual offers1.toSet
//
//
//      val findOneParams = OfferSearchParams(ids = Some(SearchParamsRule(true, NonEmptyList(offers1.head.offerId, Nil))))
//      val anotherParams = OfferSearchParams(ids = Some(SearchParamsRule(true, NonEmptyList(offers2.head.offerId, Nil))))
//      val one = dao.find(findOneParams, Some(customer.name)).unsafeRunSync()
//      one.size shouldBe 1
//      one.head shouldEqual offers1.head
//      val another = dao.find(anotherParams, Some(customer.name)).unsafeRunSync()
//      another.size shouldBe 0
//
//      netDao.deleteByName(network1.name).unsafeRunSync()
//      netDao.deleteByName(network2.name).unsafeRunSync()
//      custDao.delete(apikey).unsafeRunSync()
//    }
//
//    "find by full-text search" in {
//      dao.deleteAll().unsafeRunSync()
//      mercDao.deleteAll().unsafeRunSync()
//
//      // search by merc name
//      val mercs = genMercs(List("Anna", "Bella", "Valentina"))
//      val offs  = mercs.map{ m => genOffer(networkNames.head).copy(merchantName = m.merchantName) }
//      mercDao.insert(mercs).unsafeRunSync()
//      dao.insert(offs).unsafeRunSync()
//      val res1 = dao.find(OfferSearchParams(search = Some("Anna"))).unsafeRunSync()
//      res1.size shouldBe 1
//
//      // search by offer desc
//      val offerWithNewDesc = withRebuildIndex(offs.filter(_.merchantName == "Bella").head.copy(offerId = randomString, offerDescription = "Anna"))
//      dao.insert(List(offerWithNewDesc)).unsafeRunSync()
//      val res2 = dao.find(OfferSearchParams(search = Some("Anna"))).unsafeRunSync()
//      res2.size shouldBe 2
//
//      // search by merc categroy
//      val mercWithCat = withRebuildIndex(genMercs(List("Dorothy")).head.copy(categories = List("Dolores","Anna","Emma")))
//      val offWithCat = withRebuildIndex(genOffer(networkNames.head).copy(merchantName = mercWithCat.merchantName))
//      mercDao.insert(List(mercWithCat)).unsafeRunSync()
//      dao.insert(List(offWithCat)).unsafeRunSync()
//      val res3 = dao.find(OfferSearchParams(search = Some("Anna"))).unsafeRunSync()
//      res3.size shouldBe 3
//
//      // search by merc description
//      val mercWithDesc = withRebuildIndex(genMercs(List("Maria")).head.copy(description = "Anna"))
//      val offWithDesc = withRebuildIndex(genOffer(networkNames.head).copy(merchantName = mercWithCat.merchantName))
//      mercDao.insert(List(mercWithDesc)).unsafeRunSync()
//      dao.insert(List(offWithDesc)).unsafeRunSync()
//      val res4 = dao.find(OfferSearchParams(search = Some("Anna"))).unsafeRunSync()
//      res4.size shouldBe 4
//    }

  }

  def genOffer(network: String): MerchantOfferRow = {
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
      override def apply: Instant = randomOneOf(times)
    }

    Generator[MerchantOfferRow].apply.copy(merchantNetwork = network)
  }

  def genMercs(names: List[String]): List[MerchantRow] = {
    names.map { name => Generator[MerchantRow].apply.copy(merchantName = name) }
  }


}
