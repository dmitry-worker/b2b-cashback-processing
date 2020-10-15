package com.noproject.common.service

import java.time.Instant
import java.time.temporal.ChronoField

import cats.effect.{ContextShift, IO, Timer}
import cats.kernel.Eq
import cats.implicits._
import com.noproject.common.Executors
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO, MerchantOfferDiffDAO}
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.domain.dao.{DefaultPersistenceTest, UnknownTransactionDAO}
import com.noproject.common.domain.model.{Location, Money}
import com.noproject.common.domain.model.customer.Consumer
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, MerchantRewardItem, MerchantRow}
import com.noproject.common.domain.model.partner.Network
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.MerchantDataService
import com.noproject.common.stream.{RabbitConfig, RabbitProducer, SimpleRabbitConfig, DefaultRabbitTest}
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import io.circe.Json
import io.prometheus.client.CollectorRegistry
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.service.PartnerTrackingService
import org.scalamock.scalatest.MockFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class CommonIntegrationServiceTest
  extends WordSpec
     with BeforeAndAfterAll
     with DefaultPersistenceTest
     with DefaultRabbitTest
     with RandomValueGenerator
     with MockFactory
     with Matchers {

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }
  private val clock = TestClock.apply

  private val timer: Timer[IO]         = IO.timer(ExecutionContext.global)
  private val shift: ContextShift[IO]  = IO.contextShift(ExecutionContext.global)

  val ndao = new NetworkDAO(xar)
  val mdao = new MerchantDAO(xar)
  val utdao = new UnknownTransactionDAO(xar)
  val modao = new MerchantOfferDAO(xar, clock)
  val modiffdao = new MerchantOfferDiffDAO(xar)
  val mds = new MerchantDataService(mdao, modao, modiffdao, xar, StaticOffersConfigProvider, clock)

  implicit val eqM = Eq.fromUniversalEquals[MerchantRow]
  implicit val eqO = Eq.fromUniversalEquals[MerchantOfferRow]

  private val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"//String
  , port     = 5672//Int
  , user     = Some("rabbit")//Option[String]
  , password = Some("rabbit")//Option[String]
  )


  private val TEST_EXCHANGE_NAME  = "txns"
  private val TEST_CUSTOMER_NAME  = "testCustomer"
  private val TEST_NETWORK_NAME   = "test"
  private val TEST_QUEUE_NAME     = TEST_EXCHANGE_NAME + ":" + TEST_CUSTOMER_NAME

  private val fs2rconf = RabbitConfig.buildConfig(conf)
  private val fs2r = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()


  private val network = Network("test", None)

  private val ((pub, sub), rclear)  = immediateRabbitBridge[CashbackTransaction](TEST_EXCHANGE_NAME, TEST_QUEUE_NAME, TEST_CUSTOMER_NAME, fs2r).allocated.unsafeRunSync()
  private val (leveldb, ldbclear)   = LevelDBPersistence.getInstance[CashbackTransaction]("example.db").allocated.unsafeRunSync()
  private val service               = new TestIntegrationService(mds, new CollectorRegistry(), pub)

  override def beforeAll(): Unit = {
    super.beforeAll()
    val io: IO[Unit] = for {
      _ <- ndao.deleteAll()
      _ <- mdao.deleteAll()
      _ <- modao.deleteAll()
      _ <- modiffdao.deleteAll()
      _ <- utdao.deleteAll()
    } yield ()
    io.unsafeRunSync()
    ndao.insert(List(network)).unsafeRunSync
  }

  override def afterAll() : Unit = {
    rclear.unsafeRunSync()
    ldbclear.unsafeRunSync()
    super.afterAll()
  }


  "CommonIntegrationServiceTest" should {

    "write offers" in {
      val merc = genMerc
      val offer = genOffer(merc.merchantName, "test")

      service.updateMerchantRows(List(merc)).unsafeRunSync()
      val persMerc = mdao.findAll.unsafeRunSync().head
      persMerc shouldEqual merc
      clock.step()
      service.updateOfferRows(List(offer), clock.instant()).unsafeRunSync()
      val persOffer = modao.findAll.unsafeRunSync().head
      persOffer shouldEqual offer

      val diff = modiffdao.findAll.unsafeRunSync()
      diff.isEmpty shouldBe true
    }

    "update offers and diffs" in {
      val merc = genMerc
      val offer = genOffer(merc.merchantName, "test")

      clock.step()
      service.updateMerchantRows(List(merc)).unsafeRunSync()
      val persMerc = mdao.findAll.unsafeRunSync().filter(_.merchantName == merc.merchantName).head
      persMerc shouldEqual merc

      clock.step()
      service.updateOfferRows(List(offer), clock.instant()).unsafeRunSync()
      val persOffer = modao.findAll.unsafeRunSync().filter(_.offerId == offer.offerId).head
      persOffer shouldEqual offer
      val diff = modiffdao.findAll.unsafeRunSync().filter(_.offerId == offer.offerId)
      diff.isEmpty shouldBe true
      val offerChanged = offer.copy(offerDescription = randomString, offerAddress = randomOptString)

      clock.step()
      service.updateOfferRows(List(offerChanged), clock.instant()).unsafeRunSync()
      val persOffer1 = modao.findAll.unsafeRunSync().filter(_.offerId == offer.offerId).head
      persOffer1 shouldEqual offerChanged
      val diff1 = modiffdao.findAll.unsafeRunSync().filter(_.offerId == offer.offerId)
      diff1.length shouldBe 1
      diff1.head.offerId shouldBe offerChanged.offerId
    }

    "save unidentified transactions into unknown txns table" in {
      val known = genTxn.copy(customerName = "testCustomer", userId = "Vasya")
      val unknown = genTxn.copy(customerName = Consumer.Unknown, userId = Consumer.Unknown)
      val txns: List[CashbackTransaction] = known :: unknown :: Nil
      val now = Instant.now()
      val io = service.submitTransactions(txns) <* timer.sleep(2 seconds)
      io.unsafeRunSync()

      sub.mvar.take.unsafeRunTimed(1 seconds).get.get.contents.head.id shouldEqual known.id
      utdao.findAll.unsafeRunTimed(1 seconds).get.head.id shouldEqual unknown.id
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
      override def apply: Instant = Instant.now
    }

    Generator[MerchantOfferRow].apply.copy(
      merchantName = name,
      merchantNetwork = net,
    )
  }

  implicit def genJson:Generator[Json] = new Generator[Json] {
    override def apply: Json = Json.fromFields(
      List("fieldName" -> Json.fromString("fieldValue"))
    )
  }

  def genMerc: MerchantRow = Generator[MerchantRow].apply

  def genTxn: CashbackTransaction = Generator[CashbackTransaction].apply


  class TestIntegrationService(
    mds:          MerchantDataService
  , cr:           CollectorRegistry
  , txnProducer:  RabbitProducer[CashbackTransaction]
  )(
  implicit val cs: ContextShift[IO]
  ) extends CommonIntegrationService("test", utdao, txnProducer, mds, mock[PartnerTrackingService], cr, clock, leveldb) {
    override def updateMerchantRows(merchants: List[MerchantRow]): IO[Unit] = super.updateMerchantRows(merchants)
    override def updateOfferRows(offers: List[MerchantOfferRow], atTime: Instant ): IO[Unit] = super.updateOfferRows(offers, atTime)
  }
}
