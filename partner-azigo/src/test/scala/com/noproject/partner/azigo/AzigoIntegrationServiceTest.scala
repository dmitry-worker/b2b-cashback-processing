package com.noproject.partner.azigo

import java.time.Clock

import cats.effect.{IO, Resource}
import com.noproject.common.Executors
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.dao.customer.ConsumerDAO
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO, MerchantOfferDiffDAO}
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.domain.dao.{ConfigDAO, DefaultPersistenceTest, UnknownTransactionDAO}
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.Percent
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.partner.Network
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.{MerchantDataService, MerchantMappingDataService}
import com.noproject.common.stream.{RabbitConfig, SimpleRabbitConfig, DefaultRabbitTest, TopicUtils}
import com.noproject.domain.model.merchant.azigo.MerchantAzigo
import com.noproject.partner.azigo.config.{AzigoAffiliateConfig, AzigoConfig, AzigoSsoConfig}
import com.noproject.partner.azigo.domain.model.AzigoTxn
import com.noproject.service.PartnerTrackingService
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import io.prometheus.client.CollectorRegistry
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class AzigoIntegrationServiceTest extends WordSpec
  with DefaultPersistenceTest
  with DefaultRabbitTest
  with MockFactory
  with RandomValueGenerator
  with BeforeAndAfterAll
  with Matchers {

  val cfg = AzigoConfig(
    AzigoAffiliateConfig("https://api.platformapis.com", "oe0149a695c414a39ef296f11d0778ac0")
  , AzigoSsoConfig("", "")
  )

  object StaticConfigProvider extends ConfigProvider[AzigoConfig] with FailFastConfigProvider[AzigoConfig] {
    override protected def load: IO[AzigoConfig] = IO.pure(cfg)
  }

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"//String
  , port     = 5672//Int
  , user     = Some("rabbit")//Option[String]
  , password = Some("rabbit")//Option[String]
  )

  private val mmds = stub[MerchantMappingDataService]
  private val emptyMappings = MerchantMappings(Map(), Map())
  (mmds.getMappings _).when().returns(IO.pure(emptyMappings)).anyNumberOfTimes

  private val clock = TestClock.apply

  private val utDAO = new UnknownTransactionDAO(xar)
  private val offerDAO = new MerchantOfferDAO(xar, clock)
  private val offerDiffDAO = new MerchantOfferDiffDAO(xar)
  private val merchDAO = new MerchantDAO(xar)
  private val netDAO = new NetworkDAO(xar)
  private val mDS = new MerchantDataService(merchDAO, offerDAO, offerDiffDAO, xar, StaticOffersConfigProvider, clock)
  private val ts = stub[PartnerTrackingService]

  private val allowedMerchants = (1 to 10).map { i => s"Merchant$i"}
  private val newAzigoMercs = (0 until 195).map(_ => genMerc(allowedMerchants)).toList
  private val newMercs  = newAzigoMercs.foldLeft(Set[String]())( (s, el) => s + el.storeName )

  private def httpClient: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](Executors.miscExec)
    .withMaxTotalConnections(5)
    .resource

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val io = for {
      _ <- merchDAO.deleteAll()
      _ <- offerDAO.deleteAll()
      _ <- netDAO.deleteAll()
      _ <- netDAO.insert(Network("azigo", None) :: Nil)
    } yield ()
    io.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    val io = for {
      _ <- merchDAO.deleteAll()
      _ <- offerDAO.deleteAll()
      _ <- netDAO.deleteAll()
      _ <- cclear
      _ <- tclear
      _ <- rclear
      _ <- ldbclear
    } yield ()
    io.unsafeRunSync()
    super.afterAll()
  }

  val exName = "offers"
  val exchange = ExchangeName(exName)

  val rkName = "azigo"
  val routing = RoutingKey(rkName)

  val qName = "offers"
  val queue = QueueName(qName)

  val fs2rconf = RabbitConfig.buildConfig(conf)
  val fs2r = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()

  private val ((pub, sub), rclear)  = immediateRabbitBridge[CashbackTransaction](exName, qName, rkName, fs2r).allocated.unsafeRunSync()
  private val (cli, cclear)         = BlazeClientBuilder[IO](Executors.miscExec).withMaxTotalConnections(1).resource.allocated.unsafeRunSync()
  private val (leveldb, ldbclear)   = LevelDBPersistence.getInstance[CashbackTransaction]("example.db").allocated.unsafeRunSync()
  private val (topic, tclear)       = TopicUtils.buildTopic[AzigoTxn].allocated.unsafeRunSync()
  private val service               = new AzigoIntegrationService(utDAO, mDS, ts, mmds, clock, StaticConfigProvider, new CollectorRegistry(), pub, cli, topic, leveldb)


  "AzigoServiceIntegrationTest" should {

    "perform an successful update using `create`" in {
      service.update0(cfg, newAzigoMercs.toList, emptyMappings, clock.instant()).unsafeRunSync()
      val persMercs = merchDAO.findAll.unsafeRunSync().map(_.merchantName).toSet
      persMercs shouldEqual newMercs
      val persOffers = offerDAO.findAll.unsafeRunSync()
      IO.pure(persOffers.size shouldEqual 195)
    }

    "perform an successful update with slightly changed data" in {
      val first = newAzigoMercs.head
      val second = newAzigoMercs.tail.head
      val lasts = newAzigoMercs.tail.tail
      val PERCENTAGE = Percent(99.99, false)
      // first is altered, second is deleted
      val altered = first.copy(userCommissionPct = PERCENTAGE.amount.toDouble) :: lasts
      val alteredSet = altered.foldLeft(Set[String]())((s, el) => s + el.storeName)
      clock.step
      service.update0(cfg, altered, emptyMappings, clock.instant()).unsafeRunSync()

      val persOffers = offerDAO.findAll.unsafeRunSync()
      val persOffersMap = persOffers.map(m => m.offerId -> m).toMap
      persOffersMap(first.storeGuid).rewardPercentBest shouldEqual PERCENTAGE

      assert(persOffersMap(second.storeGuid).whenDeactivated.exists(clock.instant ==))
    }

  }



  def genMerc(allowedNames: Seq[String]): MerchantAzigo = {
    Generator[MerchantAzigo].apply.copy(storeName = randomOneOf(allowedNames))
  }

  val rawTransactions =
    """
      |[{
      |	"programName": "PRG",
      |	"userEmail": "",
      |	"status": "user",
      |	"uniqueRecordId": "a6bc519f82fe6d7ee972d7456624bd154e31517fbc49777e99e61410146578b6",
      |	"suppliedSubProgramId": "",
      |	"transactionId": 47112006,
      |	"storeOrderId": "9119973897547",
      |	"userId": "152608900663078400",
      |	"suppliedUserId": "21e3cab73fed44568c5ea3f6c45f16c9",
      |	"storeName": "Target",
      |	"tentativeCannotChangeAfterDate": 1536969600,
      |	"tentativeCannotChangeAfterDatetime": "2018-09-15T00:00:00.000Z",
      |	"timestamp": 1530569679,
      |	"datetime": "2018-07-02T22:14:39.000Z",
      |	"postDatetime": "2018-10-03T15:30:02.000Z",
      |	"sale": 21.84,
      |	"commission": 0.44,
      |	"userCommission": 0.33,
      |	"sourceType": "site",
      |	"resellerPayoutDate": null,
      |	"logoUrl": "https://s3.amazonaws.com/storeslogo/459720"
      |}, {
      |	"programName": "PRG",
      |	"userEmail": "",
      |	"status": "user",
      |	"uniqueRecordId": "4f345d6993c8768b6444e7b7647eaf1f0b11939db739bdb20844811b5c6048c3",
      |	"suppliedSubProgramId": "",
      |	"transactionId": 47130906,
      |	"storeOrderId": "2CAE65F8-13E9-468E-86B8-9699711ACA3E",
      |	"userId": "152894194613266080",
      |	"suppliedUserId": "8f9f0a6182fa43ba9320a76350436a58",
      |	"storeName": "Fandango",
      |	"tentativeCannotChangeAfterDate": 1533168000,
      |	"tentativeCannotChangeAfterDatetime": "2018-08-02T00:00:00.000Z",
      |	"timestamp": 1530576927,
      |	"datetime": "2018-07-03T00:15:27.000Z",
      |	"postDatetime": "2018-10-03T15:30:02.000Z",
      |	"sale": 15,
      |	"commission": 0.2,
      |	"userCommission": 0.15,
      |	"sourceType": "site",
      |	"resellerPayoutDate": null,
      |	"logoUrl": "https://s3.amazonaws.com/storeslogo/11420344"
      |}, {
      |	"programName": "PRG",
      |	"userEmail": "",
      |	"status": "user",
      |	"uniqueRecordId": "5f2bf66cb9122dee3e22b7811afff63424b9a219f397edd9014864a913517e4e",
      |	"suppliedSubProgramId": "",
      |	"transactionId": 47137217,
      |	"storeOrderId": "4501890128170",
      |	"userId": "153005981777040450",
      |	"suppliedUserId": "114feeac1ee14afe8bb22e716406600d",
      |	"storeName": "Wal-Mart.com USA, LLC",
      |	"tentativeCannotChangeAfterDate": 1535760000,
      |	"tentativeCannotChangeAfterDatetime": "2018-09-01T00:00:00.000Z",
      |	"timestamp": 1530229080,
      |	"datetime": "2018-06-28T23:38:00.000Z",
      |	"postDatetime": "2018-10-03T15:30:02.000Z",
      |	"sale": 79.98,
      |	"commission": 3.199,
      |	"userCommission": 2.399,
      |	"sourceType": "site",
      |	"resellerPayoutDate": null,
      |	"logoUrl": "https://s3.amazonaws.com/storeslogo/22149"
      |}]
    """.stripMargin

}
