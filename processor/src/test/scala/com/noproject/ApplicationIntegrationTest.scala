package com.noproject

import java.time.Instant
import java.time.temporal.ChronoField

import cats.data.NonEmptyList
import cats.implicits._
import cats.effect.{IO, Resource}
import com.google.inject.Guice
import com.noproject.common.Executors
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.dto.TransactionSearchParams
import com.noproject.common.domain.dao.{CashbackTransactionDAO, EventLogDao, DefaultPersistenceTest}
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.eventlog.{EventLogItem, EventLogObjectType}
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import com.noproject.common.stream.{RabbitConfig, RabbitProducer, SimpleRabbitConfig, DefaultRabbitTest}
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, Matchers, WordSpec}

import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.Money
import com.noproject.service.{TxnDeliveryService, TxnSubscriberService}
import com.typesafe.config.ConfigFactory
import io.prometheus.client.CollectorRegistry
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.metrics.prometheus.PrometheusExportService

import scala.concurrent.ExecutionContext


class ApplicationIntegrationTest extends WordSpec with DefaultPersistenceTest with DefaultRabbitTest with Matchers with RandomValueGenerator with BeforeAndAfterAll {

  val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"//String
  , port     = 5672//Int
  , user     = Some("rabbit")//Option[String]
  , password = Some("rabbit")//Option[String]
  )

  private val parentConfig  = ConfigFactory.defaultApplication()
  private val customerName  = parentConfig.getString("customer")
  private val timer = IO.timer(ExecutionContext.global)

  lazy val dao = new CashbackTransactionDAO(xar)
  lazy val evdao = new EventLogDao(xar)

  private val fs2rconf = RabbitConfig.buildConfig(conf)
  private val fs2r = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()


  // txns
  val txnExchangeName = "txns"
  val txnExchange     = ExchangeName(txnExchangeName)

  val txnRoutingKeyName = s"txns:${customerName}"
  val txnRoutingKey     = RoutingKey(txnRoutingKeyName)

  val txnQueueName = "txns"
  val txnQueue     = QueueName(txnQueueName)

  // evts
  val evtRoutingKeyName = s"${EventLogObjectType.CashbackTxn.entryName}"
  val evtRoutingKey     = RoutingKey(evtRoutingKeyName)

  val evtExchangeName = "events"
  val evtExchange     = ExchangeName(evtExchangeName)

  val evtQueueName  = "events"
  val evtQueue      = QueueName(evtQueueName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    val io = for {
      _ <- dao.deleteAll()
      _ <- evdao.deleteAll()
    } yield ()
    io.unsafeRunSync()
  }

  private val (client, shutdown) = {
    BlazeClientBuilder[IO](Executors.miscExec)
      .withMaxTotalConnections(2)
      .resource
      .allocated
      .unsafeRunSync()
  }

  private val ((tprod, tcons), tclear)  = immediateRabbitBridge[CashbackTransaction](txnExchangeName, txnQueueName, txnRoutingKeyName, fs2r).allocated.unsafeRunSync()
  private val ((eprod, econs), eclear)  = immediateRabbitBridge[EventLogItem](evtExchangeName, evtQueueName, evtRoutingKeyName, fs2r).allocated.unsafeRunSync()
  private val injector = Guice.createInjector(new ProcessorModule(parentConfig, EnvironmentMode.Test, xar, eprod, tprod, tcons, PrometheusExportService.apply[IO](new CollectorRegistry()), client))
//  private val dlvService = injector.getInstance(classOf[TxnDeliveryService])
  private val subService = injector.getInstance(classOf[TxnSubscriberService])
  subService.runForever.start.unsafeRunSync()
  econs.drainWithIO {
    sre => evdao.insertMany(sre.contents).void
  }.start.unsafeRunSync()

  override def afterAll(): Unit = {
    val io = for {
      _ <- dao.deleteAll()
      _ <- evdao.deleteAll()
      _ <- shutdown
      _ <- tclear
      _ <- eclear
    } yield ()
    io.unsafeRunSync()
    super.afterAll()
  }

  "Application" should {

    "send a txn to be persisted" in {
      val txn = genTxn
      // TODO: this is an example. Please prefer `timer.sleep` over `Thread.sleep`
      val io = tprod.submit(txnRoutingKeyName, txn :: Nil) *> timer.sleep(2 seconds)
      io.unsafeRunSync()
      val found = dao.find(TransactionSearchParams().withIds(NonEmptyList(txn.id, Nil))).unsafeRunSync()
      dao.deleteAll().unsafeRunSync()
      assert(CashbackTransaction.eq.eqv(txn, found.head))
    }

    "send an pure event" in {
      evdao.deleteAll().unsafeRunSync()
      val event = genEvent
      val ioP = eprod.submit(evtRoutingKeyName, event :: Nil) *> timer.sleep(2 seconds)
      ioP.unsafeRunSync()

      val found = evdao.findAll.unsafeRunSync().head
      evdao.deleteAll().unsafeRunSync()
      event.timestamp  shouldEqual found.timestamp
      event.rawObject  shouldEqual found.rawObject
      event.objectId   shouldEqual found.objectId
      event.objectType shouldEqual found.objectType
    }

    "send event made from txn to event pub" in {
      val event = genTxn.asEventLogItem("")
      val ioP = eprod.submit(evtRoutingKeyName, event :: Nil) *> timer.sleep(2 seconds)
      ioP.unsafeRunSync()
      val found = evdao.findAll.unsafeRunSync().head
      evdao.deleteAll().unsafeRunSync()
      event.timestamp  shouldEqual found.timestamp
      event.rawObject  shouldEqual found.rawObject
      event.objectId   shouldEqual found.objectId
      event.objectType shouldEqual found.objectType
    }

    "send invalid txn and raise event" in {
      val whenPurchased = Instant.now.plusSeconds(1000.days.toSeconds).`with`(ChronoField.NANO_OF_SECOND, 0L)
      val txn = genTxn.copy(purchaseDate = whenPurchased)
      val io = tprod.submit(txnRoutingKeyName, txn :: Nil) *> timer.sleep(5 seconds)
      io.unsafeRunSync()

      val found = evdao.findAll.unsafeRunSync().head
      found.objectId shouldEqual Some(txn.id.toString)
      found.objectType shouldEqual EventLogObjectType.CashbackTxn
      val rawToTxn = Decoder[CashbackTransaction].decodeJson(found.rawObject.get)

      rawToTxn.right.get shouldEqual txn
      assert ( CashbackTransaction.eq.eqv(rawToTxn.right.get, txn) )
    }

    "send a txn to be persisted and updated" in {
      val someTxn = genTxn
      val ioSrc = tprod.submit(txnRoutingKeyName, someTxn :: Nil) *> timer.sleep(2 seconds)
      ioSrc.unsafeRunSync()
      val foundTxns = dao.find(TransactionSearchParams().withIds(NonEmptyList(someTxn.id, Nil))).unsafeRunSync()
      assert(CashbackTransaction.eq.eqv(someTxn, foundTxns.head))

      val txnUpdate = someTxn.copy(status = CashbackTxnStatus.Paid)
      val ioUpdate = tprod.submit(txnRoutingKeyName, txnUpdate :: Nil) *> timer.sleep(2 seconds)
      ioUpdate.unsafeRunSync()
      val foundUpdate = dao.find(TransactionSearchParams().withIds(NonEmptyList(someTxn.id, Nil))).unsafeRunSync()
      assert(CashbackTransaction.eq.eqv(txnUpdate, foundUpdate.head))
    }
  }

  def genEvent = EventLogItem(
    None
  , Instant.now.`with`(ChronoField.NANO_OF_SECOND, 0L)
  , EventLogObjectType.CashbackTxn
  , Some("id")
  , None
  , "message"
  , None
  )


  def genTxn: CashbackTransaction = {
    val now = Instant.now.minusSeconds(randomInt(60*60*24)).`with`(ChronoField.NANO_OF_SECOND, 0L)
    val amount = BigDecimal(randomInt(1000))
    CashbackTransaction(
      id = randomUUID
    , userId = randomString
    , customerName = "customer"
    , reference = randomString
    , merchantName = "7-eleven"
    , merchantNetwork = "azigo"
    , description = None
    , whenCreated = now
    , whenUpdated = now
    , whenClaimed = None
    , whenSettled = None
    , whenPosted = None
    , purchaseDate = now
    , purchaseAmount = Money(amount)
    , purchaseCurrency = "USD"
    , cashbackBaseUSD = Money(amount * 0.10)
    , cashbackTotalUSD = Money(amount * 0.10)
    , cashbackUserUSD = Money(amount * 0.03)
    , cashbackOwnUSD = Money(amount * 0.07)
    , status = CashbackTxnStatus.Pending
    , parentTxn = None
    , payoutId = None
    , failedReason = None
    , rawTxn = Json.fromFields(Nil)
    , offerId = randomOptString
    , offerTimestamp = Some(now)
    )
  }

}
