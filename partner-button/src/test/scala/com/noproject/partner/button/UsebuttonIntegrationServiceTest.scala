package com.noproject.partner.button

import java.time.{Clock, Instant}

import cats.effect.{ContextShift, IO, Resource, Timer}
import com.noproject.common.Executors
import com.noproject.common.codec.json.InstantCodecs
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}
import com.noproject.common.domain.dao.customer.ConsumerDAO
import com.noproject.common.domain.dao.merchant._
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.domain.dao.{ConfigDAO, DefaultPersistenceTest, UnknownTransactionDAO}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.partner.Network
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service._
import com.noproject.common.stream.{RabbitConfig, SimpleRabbitConfig, DefaultRabbitTest, TopicUtils}
import com.noproject.partner.button.config.UsebuttonConfig
import com.noproject.partner.button.domain.dao.UsebuttonMerchantDAO
import com.noproject.partner.button.domain.model._
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.service.PartnerTrackingService
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.prometheus.client.CollectorRegistry
import javax.inject.Inject
import org.http4s.Uri
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

class UsebuttonIntegrationServiceTest extends WordSpec
  with DefaultPersistenceTest
  with DefaultRabbitTest
  with UsebuttonCodecs
  with InstantCodecs
  with MockFactory
  with RandomValueGenerator
  with BeforeAndAfterAll
  with Matchers {

  val cfg = UsebuttonConfig(
    webhookSecret = "secret"
  , apiKey = "sk-3SDRrPgCTuk8dkHI2uI0Fg"
  , apiSecret = ""
  , expirationMinutes = 60
  , url = "https://api.usebutton.com"
  , organizationId = "org-xxx"
  , accountId = "acc-xxx"
  )

  object StaticConfigProvider extends ConfigProvider[UsebuttonConfig] with FailFastConfigProvider[UsebuttonConfig] {
    override protected def load: IO[UsebuttonConfig] = IO.pure(cfg)
  }

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  private val httpClient: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](Executors.miscExec)
    .withMaxTotalConnections(5)
    .resource

  val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"//String
  , port     = 5672//Int
  , user     = Some("rabbit")//Option[String]
  , password = Some("rabbit")//Option[String]
  )

  private val emptyMappings = MerchantMappings(Map(), Map())
//  (mmds.getMappings _).when().returns(IO.pure(emptyMappings)).anyNumberOfTimes
  
  private val clock = TestClock.apply

  private val configDAO = new ConfigDAO(xar)
  private val offerDAO = new MerchantOfferDAO(xar, clock)
  private val offerDiffDAO = new MerchantOfferDiffDAO(xar)
  private val merchDAO = new MerchantDAO(xar)
  private val netDAO = new NetworkDAO(xar)
  private val ubmDAO = new UsebuttonMerchantDAO(xar)
  private val cuDAO = new ConsumerDAO(xar)
  private val utDAO = new UnknownTransactionDAO(xar)
  private val mDS = new MerchantDataService(merchDAO, offerDAO, offerDiffDAO, xar, StaticOffersConfigProvider, clock)
  private val ts = stub[PartnerTrackingService]

  private val mnmd = new MerchantNameMappingDAO(xar)
  private val mcmd = new MerchantCategoryMappingDAO(xar)
  private val mmds = new MerchantMappingDataServiceImpl(mnmd, mcmd)

  private val moment1 = TestClock.apply.instant()
  private val moment2 = moment1.plusMillis(1000)
  private val allowedMerchants = (1 to 10).map { i => s"Merchant$i"}
  private val newUBMercs = (0 until 195).map(_ => genMerc(allowedMerchants)).toList
  private val newUBMercsRow = (0 until 195).map(_ => genMercRow).toList
  private val newMercs  = newUBMercs.foldLeft(Set[String]())( (s, el) => s + el.name )
  private val newMercsRow  = newUBMercsRow.foldLeft(Set[String]())( (s, el) => s + el.name )

  private val exName = "offers"
  private val exchange = ExchangeName(exName)
  private val rkName = "azigo"
  private val routing = RoutingKey(rkName)
  private val qName = "offers"
  private val queue = QueueName(qName)

  private val fs2rconf = RabbitConfig.buildConfig(conf)
  private val fs2r = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()

  private val ((pub, sub), rclear)  = immediateRabbitBridge[CashbackTransaction](exName, qName, rkName, fs2r).allocated.unsafeRunSync()
  private val (cli, cclear)         = BlazeClientBuilder[IO](Executors.miscExec).withMaxTotalConnections(1).resource.allocated.unsafeRunSync()
  private val (leveldb, ldbclear)   = LevelDBPersistence.getInstance[CashbackTransaction]("example.db").allocated.unsafeRunSync()
  private val (topic, tclear)       = TopicUtils.buildTopic[UsebuttonPayload].allocated.unsafeRunSync()
  private val service               = new UseButtonIntegrationService(utDAO, mDS, ubmDAO, ts, mmds, Clock.systemUTC(), StaticConfigProvider, new CollectorRegistry(), pub, cli, topic, leveldb)


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val io = for {
      _ <- merchDAO.deleteAll()
      _ <- offerDAO.deleteAll()
      _ <- ubmDAO.deleteAll()
      _ <- netDAO.deleteAll()
      _ <- netDAO.insert(Network("usebutton", None) :: Nil)
    } yield ()
    io.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    val io = for {
      _ <- merchDAO.deleteAll()
      _ <- offerDAO.deleteAll()
      _ <- ubmDAO.deleteAll()
      _ <- netDAO.deleteAll()
      _ <- rclear
      _ <- cclear
      _ <- ldbclear
      _ <- tclear
    } yield ()
    io.unsafeRunSync()
    super.afterAll()
  }

  "UsebuttonIntegrationServiceTest" should {

    "parse merc's json" in {
      val json =
        """
          |{
          |  "meta" : {
          |    "status" : "ok"
          |  },
          |  "objects" : [
          |    {
          |      "id" : "org-228b55a5707de5c8",
          |      "name" : "Button Test Merchant (Sandbox)",
          |      "categories" : [
          |        "E-Commerce"
          |      ],
          |      "metadata" : {
          |        "description" : "Button's Test Merchant",
          |        "icon_url" : "https://button.imgix.net/org-228b55a5707de5c8/icon/37852d867dd8185c.jpg",
          |        "banner_url" : "not supported yet"
          |      },
          |      "urls" : {
          |        "homepage" : "https://buttontestmerchant.com",
          |        "terms_and_conditions" : null
          |      },
          |      "available_platforms" : [
          |        "web",
          |        "ios",
          |        "android"
          |      ],
          |      "supported_products" : [
          |      ],
          |      "status" : "approved",
          |      "additional_terms" : null,
          |      "exclusion_details" : null
          |    }
          |  ]
          |}
        """.stripMargin
      val res = Decoder[UsebuttonMerchantsResponse].decodeJson(parse(json).right.get).right.get
      println(res)
    }

    "parse offers' json" in {
      val json =
        """
          |{
          |    "meta": {
          |        "status": "ok"
          |    },
          |    "object": {
          |        "merchant_offers": [
          |            {
          |                "merchant_id": "org-228b55a5707de5c8",
          |                "best_offer_id": "offer-5cd2c3107c0a0e3e-0",
          |                "offers": [
          |                    {
          |                        "id": "offer-5cd2c3107c0a0e3e-0",
          |                        "rate_percent": "10",
          |                        "display_params": {
          |                            "category": "Toys"
          |                        }
          |                    },
          |                    {
          |                        "id": "offer-5cd2c3107c0a0e3e-1",
          |                        "rate_percent": "5",
          |                        "display_params": {
          |                            "category": "Furniture"
          |                        }
          |                    },
          |                    {
          |                        "id": "offer-5cd2c3107c0a0e3e-2",
          |                        "rate_percent": "2",
          |                        "display_params": {
          |                            "category": "Electronics"
          |                        }
          |                    }
          |                ]
          |            }
          |        ]
          |    }
          |}
        """.stripMargin
      val res = Decoder[UsebuttonOffersResponse].decodeJson(parse(json).right.get).right.get
      println(res)
    }

    "parse txn json" in {
      val json =
        """
          |{
          |  "request_id" : "attempt-XXX",
          |  "data" : {
          |    "posting_rule_id" : null,
          |    "order_currency" : "USD",
          |    "modified_date" : "2019-10-17T20:00:00.000Z",
          |    "created_date" : "2019-10-16T20:00:00.000Z",
          |    "order_line_items" : [
          |      {
          |        "identifier" : "sku-1234",
          |        "total" : 600,
          |        "amount" : 2000,
          |        "quantity" : 3,
          |        "publisher_commission" : 1000,
          |        "sku" : "sku-1234",
          |        "upc" : "400000000001",
          |        "category" : [
          |          "Clothes"
          |        ],
          |        "description" : "T-shirts",
          |        "attributes" : {
          |          "size" : "M"
          |        }
          |      }
          |    ],
          |    "button_id" : "btn-XXX",
          |    "campaign_id" : "camp-XXX",
          |    "rate_card_id" : "ratecard-XXX",
          |    "order_id" : "order-1",
          |    "customer_order_id" : "abcdef-123456",
          |    "account_id" : "acc-XXX",
          |    "btn_ref" : "srctok-XXYYZZ",
          |    "currency" : "USD",
          |    "pub_ref" : "publisher-token",
          |    "status" : "pending",
          |    "event_date" : "2019-10-15T20:00:00Z",
          |    "order_total" : 600,
          |    "advertising_id" : "aaaaaaaa-1111-3333-4444-999999999999",
          |    "publisher_organization" : "org-XXX",
          |    "commerce_organization" : "org-XXX",
          |    "amount" : 1000,
          |    "button_order_id" : "btnorder-XXX",
          |    "publisher_customer_id" : "YjZmYTQ4NTZhMzY4Mzg2ZmIyMGVmZDJhZmU4M2EyOWR8b3JnLTIyOGI1NWE1NzA3ZGU1Yzh8MTU3MzEyOTc4MDYyNA==",
          |    "id" : "4746",
          |    "order_click_channel" : "app",
          |    "category" : "new-user-order",
          |    "validated_date" : "2019-10-18T19:02:09Z"
          |  },
          |  "id" : "hook-XXX",
          |  "event_type" : "tx-validated"
          |}
        """.stripMargin
      val res = Decoder[UsebuttonTxn].decodeJson(parse(json).right.get).right.get
      println(res)
    }

    "sync offers using offer stubs and service methods" in {
      service.update0(cfg, newUBMercs, emptyMappings, moment1).unsafeRunSync()
      val persMercs = merchDAO.findAll.unsafeRunSync().map(_.merchantName).toSet
      persMercs shouldEqual newMercs
      val persOffers = offerDAO.findAll.unsafeRunSync()
      IO.pure(persOffers.size shouldEqual 195)
    }

    "sync offers using rest api" in {
      service.syncUsebuttonOffersWithRestApi.unsafeRunSync()
      val persMercs = merchDAO.findAll.unsafeRunSync()
      persMercs.size shouldBe 1
      persMercs.head.merchantName shouldEqual "Button Test Merchant (Sandbox)"
      val persOffers = offerDAO.findAll.unsafeRunSync()
      persOffers.size shouldBe 1
      persOffers.head.trackingRule.get shouldBe "https://r.bttn.io?btn_pub_user={userId}&btn_url=https%3A%2F%2Fbuttontestmerchant.com&btn_ref=" + cfg.organizationId
    }

//    this method is not supported right now, but i want to keep this fragment - maybe we should return db sync in further
//    "sync offers using db" in {
//      beforeAll()
//
//      val testio = fs2r.createConnectionChannel use { implicit channel =>
//        val sr = CommonRabbit(fs2r, channel)
//        val service = new UseButtonIntegrationService(sr, mDS, ubmDAO, ts, mmds, Clock.systemUTC(), StaticConfigProvider, new CollectorRegistry())
//
//        ubmDAO.insert(newUBMercsRow).unsafeRunSync()
//        service.syncUsebuttonOffersWithDb.unsafeRunSync()
//
//        val persMercs = merchDAO.findAll.unsafeRunSync().map(_.merchantName).toSet
//        persMercs shouldEqual newMercsRow
//        val persOffers = offerDAO.findAll.unsafeRunSync()
//        IO.pure(persOffers.size shouldEqual 195)
//      }
//      testio.unsafeRunSync()
//    }

  }

  def genMerc(allowedNames: Seq[String]): UsebuttonMerchant = {
    implicit val genU = Generator[UsebuttonMerchantUrls]
    implicit val genR = Generator[UsebuttonRewardItem]
    implicit val genMS = new Generator[Map[String,String]] { override def apply: Map[String,String] = Map.empty }
    implicit val genMU = new Generator[Map[String,UsebuttonRewardItem]] { override def apply: Map[String,UsebuttonRewardItem] = Map.empty }
    Generator[UsebuttonMerchant].apply.copy(name = randomOneOf(allowedNames))
  }


  def genMercRow: UsebuttonMerchantRow = {
    implicit val genU = Generator[UsebuttonMerchantUrls]
    implicit val genR = Generator[UsebuttonRewardItem]
    implicit val genMS = new Generator[Map[String,String]] { override def apply: Map[String,String] = Map.empty }
    implicit val genMU = new Generator[Map[String,UsebuttonRewardItem]] { override def apply: Map[String,UsebuttonRewardItem] = Map.empty }
    implicit val genJ = new Generator[Json] { override def apply: Json = Json.fromValues(Nil) }
    Generator[UsebuttonMerchantRow].apply
  }


}
