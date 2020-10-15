package com.noproject.partner.coupilia.service

import java.time.Clock

import cats.effect.{IO, Resource}
import com.noproject.common.Executors
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}
import com.noproject.common.data.gen.{RandomValueGenerator, TestClock}
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.dao.{DefaultPersistenceTest, UnknownTransactionDAO}
import com.noproject.common.domain.dao.customer.ConsumerDAO
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO, MerchantOfferDiffDAO}
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.{MerchantDataService, MerchantMappingDataService}
import com.noproject.common.stream.{RabbitConfig, SimpleRabbitConfig, DefaultRabbitTest, TopicUtils}
import com.noproject.partner.coupilia.config.CoupiliaConfig
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import io.prometheus.client.CollectorRegistry
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.partner.coupilia.domain.model.CoupiliaTxn
import com.noproject.service.PartnerTrackingService
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class CoupiliaIntegrationServiceTest extends WordSpec
  with DefaultPersistenceTest
  with DefaultRabbitTest
  with MockFactory
  with RandomValueGenerator
  with BeforeAndAfterAll
  with Matchers {

  val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"//String
  , port     = 5672//Int
  , user     = Some("rabbit")//Option[String]
  , password = Some("rabbit")//Option[String]
  )

//  coupilia {
//    merchantUrl = "https://www.coupilia.com/feeds/merchants_json.cfm"
//    couponUrl = "https://www.coupilia.com/feeds/coupon_json.cfm"
//    txnUrl = "https://www.coupilia.com/feeds/reports_json.cfm"
//    secret = "12345678"
//  }

  object StaticConfigProvider extends ConfigProvider[CoupiliaConfig] with FailFastConfigProvider[CoupiliaConfig] {
    val value = CoupiliaConfig(
      "secret"
    , "https://www.coupilia.com/feeds/merchants_json.cfm"
    , "https://www.coupilia.com/feeds/coupons_json.cfm"
    , "https://www.coupilia.com/feeds/reports_json.cfm"
    )
    override def load: IO[CoupiliaConfig] = IO.pure(value)
  }

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  private val clock = TestClock.apply

  private val exName = "offers"
  private val exchange = ExchangeName(exName)
  private val rkName = "coupilia"
  private val routing = RoutingKey(rkName)
  private val qName = "offers"
  private val queue = QueueName(qName)
  private val fs2rconf: Fs2RabbitConfig = RabbitConfig.buildConfig(conf)
  private val fs2r: Fs2Rabbit[IO] = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()

  private val mmds = stub[MerchantMappingDataService]

  private val utDAO         = new UnknownTransactionDAO(xar)
  private val offerDAO      = new MerchantOfferDAO(xar, clock)
  private val offerDiffDAO  = new MerchantOfferDiffDAO(xar)
  private val merchDAO      = new MerchantDAO(xar)
  private val mDS = new MerchantDataService(merchDAO, offerDAO, offerDiffDAO, xar, StaticOffersConfigProvider, clock)

  private val ts = stub[PartnerTrackingService]

  private val ((pub, sub), rclear)  = immediateRabbitBridge[CashbackTransaction](exName, qName, rkName, fs2r).allocated.unsafeRunSync()
  private val (leveldb, ldbclear)   = LevelDBPersistence.getInstance[CashbackTransaction]("example.db").allocated.unsafeRunSync()
  private val (cli, cclear)         = BlazeClientBuilder[IO](Executors.miscExec).withMaxTotalConnections(1).resource.allocated.unsafeRunSync()
  private val (topic, tclear)       = TopicUtils.buildTopic[CoupiliaTxn].allocated.unsafeRunSync()
  private val service               = new CoupiliaIntegrationService(utDAO, mDS, mmds, StaticConfigProvider, new CollectorRegistry(), ts, cli, clock, pub, topic, leveldb)


  override def beforeAll: Unit = {
    super.beforeAll()
  }

  override def afterAll: Unit = {
    val io = for {
      _ <- rclear
      _ <- cclear
      _ <- tclear
      _ <- ldbclear
    } yield ()
    io.unsafeRunSync()
    super.afterAll()
  }

  "CoupiliaIntegrationServiceTest" should {

    "getMerchants" in {
      val testIO = service.getMerchants(StaticConfigProvider.value)
      val result = testIO.unsafeRunSync()
      println(s"Result: ${result}")
      succeed
    }

    "getOffers" in {
      val testIO = service.getOffers(StaticConfigProvider.value)
      val result = testIO.unsafeRunSync()
      println(s"Result: ${result}")
      succeed
    }

  }
}
