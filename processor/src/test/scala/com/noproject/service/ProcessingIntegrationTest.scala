package com.noproject.service

import java.time.{Clock, Instant}

import cats.implicits._
import cats.data.NonEmptyList
import cats.effect.{ContextShift, Fiber, IO, Resource, Timer}
import com.google.inject.Guice
import com.noproject.ProcessorModule
import com.noproject.common.Executors
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.partner.{CustomerNetworksDAO, NetworkDAO}
import com.noproject.common.domain.dao.transaction.{TxnChangeLogDAO, TxnDeliveryLogDAO, TxnDeliveryQueueDAO}
import com.noproject.common.domain.dao.{CashbackTransactionDAO, DefaultPersistenceTest}
import com.noproject.common.domain.model.Money
import com.noproject.common.domain.model.transaction.CashbackTxnStatus._
import com.noproject.common.domain.model.customer.{AccessRole, Customer}
import com.noproject.common.domain.model.eventlog.{EventLogItem, EventLogObjectType}
import com.noproject.common.domain.model.partner.Network
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import com.noproject.common.stream.impl.ImmediateRabbitProducer
import com.noproject.common.stream.{RabbitConfig, RabbitConsuming, RabbitProducer, SimpleRabbitConfig, DefaultRabbitTest}
import com.typesafe.config.ConfigFactory
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import doobie.implicits._
import io.chrisdavenport.fuuid.FUUID
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.prometheus.client.CollectorRegistry
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.metrics.prometheus.PrometheusExportService
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import org.http4s.{HttpRoutes, Response, Status}
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, Matchers, WordSpec}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


@DoNotDiscover
class ProcessingIntegrationTest extends WordSpec with DefaultPersistenceTest with DefaultRabbitTest with Matchers with RandomValueGenerator with BeforeAndAfterAll {
  import com.noproject.common.codec.json.ElementaryCodecs._

  implicit val shift: ContextShift[IO]  = IO.contextShift(ExecutionContext.global)

  val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"
  , port     = 5672
  , user     = Some("rabbit")
  , password = Some("rabbit")
  )

  private var destroyServer: IO[Unit] = _
  private var destroyRabbit: IO[Unit] = _
  private var destroyClient: IO[Unit] = _

  private var client: Client[IO]  = _
  private var tprod: RabbitProducer[CashbackTransaction] = _
  private var tcons: RabbitConsuming[CashbackTransaction] = _
  private var eprod: RabbitProducer[EventLogItem]        = _

  private var subService: TxnSubscriberService = _
  private var subFiber:   Fiber[IO, Unit] = _
  private var dlvService: TxnDeliveryService = _

  private val parentConfig  = ConfigFactory.defaultApplication()

  private val txnDAO         = new CashbackTransactionDAO(xar)
  private val txnQueueDAO    = new TxnDeliveryQueueDAO(xar, Clock.systemUTC)
  private val txnLogDAO      = new TxnDeliveryLogDAO(xar, Clock.systemUTC)
  private val txnChangeDAO   = new TxnChangeLogDAO(xar, Clock.systemUTC)
  private val merchNetDAO    = new NetworkDAO( xar )
  private val custNetDAO     = new CustomerNetworksDAO( xar )
  private val customerDAO    = new CustomerDAO(xar)

  val TXNS_EXCHANGE_NAME  = "txns"
  val EVTS_EXCHANGE_NAME  = "events"
  val TEST_CUSTOMER_NAME  = "testCustomer"
  val TEST_NETWORK_NAME   = "testCoupilia"
  val QUEUE_NAME          = TXNS_EXCHANGE_NAME + ":" + TEST_CUSTOMER_NAME


  private val txn1  = genTxn.copy(customerName = TEST_CUSTOMER_NAME, status = Pending)
  private val txn2  = genTxn.copy(customerName = TEST_CUSTOMER_NAME, status = Pending)

  private val fs2r = Fs2Rabbit.apply[IO](RabbitConfig.buildConfig(conf)).unsafeRunSync()

  private val successfulWebhook = "http://127.0.0.1:8080/transaction/200"
  private val retryWebhook = "http://127.0.0.1:8080/transaction/500"
  private val rejectWebhook = "http://127.0.0.1:8080/transaction/400"


  private val customer = Customer(
    name          = TEST_CUSTOMER_NAME
  , apiKey        = "testApiKey"
  , hash          = "testHash"
  , role          = Set(AccessRole.Customer)
  , webhookUrl    = Some(successfulWebhook)
  , webhookKey    = Some("12345678990")
  , webhookActive = true
  )

  private val network = Network( TEST_NETWORK_NAME, Some( "Coupilia test network" ) )

  private def cleanDB: Unit = {
    val io = for {
      _ <- txnQueueDAO.deleteAll()
      _ <- txnQueueDAO.deleteAll()
      _ <- custNetDAO.deleteAll()
      _ <- merchNetDAO.deleteAll()
      _ <- customerDAO.deleteAll()
      _ <- txnLogDAO.deleteAll()
      _ <- txnChangeDAO.deleteAll()
      _ <- txnDAO.deleteAll()
    } yield ()
    io.unsafeRunSync()
  }

  private def setupCustomer(localCustomer : Customer = customer): Unit = {
    val mkdb = for {
      _ <- customerDAO.insertTxn( localCustomer :: Nil)
      _ <- merchNetDAO.insertTxn( List( network ) )
      _ <- custNetDAO.insert( localCustomer.name, NonEmptyList.one( network.name ) )
    } yield ()
    mkdb.transact(xar.xar).unsafeRunSync()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    cleanDB
    setupCustomer()

    buildServer.allocated.map {
      case (_, free) =>
        destroyServer = free
    }.unsafeRunSync()

    val tuple1 = immediateRabbitBridge[CashbackTransaction](
      TXNS_EXCHANGE_NAME
    , TXNS_EXCHANGE_NAME + ":" + TEST_CUSTOMER_NAME
    , TEST_CUSTOMER_NAME
    , fs2r
    ).allocated.unsafeRunSync
    tprod = tuple1._1._1
    tcons = tuple1._1._2

    val tuple2 = immediateRabbitBridge[EventLogItem](
      EVTS_EXCHANGE_NAME
    , EVTS_EXCHANGE_NAME + ":" + EventLogObjectType.CashbackTxn.entryName
    , EventLogObjectType.CashbackTxn.entryName
    , fs2r
    ).allocated.unsafeRunSync
    eprod = tuple2._1._1

    buildClient.allocated.map {
      case (hc, free) =>
        client = hc
        destroyClient = free
    }.unsafeRunSync()

    val injector = Guice.createInjector(new ProcessorModule(parentConfig, EnvironmentMode.Test, xar, eprod, tprod, tcons, PrometheusExportService.apply[IO](new CollectorRegistry()), client))
    dlvService = injector.getInstance(classOf[TxnDeliveryService])
    subService = injector.getInstance(classOf[TxnSubscriberService])
    subService.runForever.start.unsafeRunSync()

  }

  override def afterAll(): Unit = {
    cleanDB
    val io = for {
      _ <- subFiber.cancel
      _ <- destroyRabbit
      _ <- destroyClient
      _ <- destroyServer
    } yield ()
    io.unsafeRunSync()
    super.afterAll()
  }

  val timer = IO.timer(ExecutionContext.global)

  "TxnProcessor" should {

    "process transactions" in {
      val io = tprod.submit(TEST_CUSTOMER_NAME, txn1 :: txn2 :: Nil) *> timer.sleep(3 seconds)
      io.unsafeRunSync()
      val rows = txnQueueDAO.findByIds(NonEmptyList(txn1.id, List(txn2.id))).unsafeRunSync()
      rows.map(_.txnId).toSet shouldEqual Set(txn1.id, txn2.id)
    }

    "deliver transactions" in {
      // deliver the transactions
      val deliveryIO = dlvService.deliver0(customer) *> timer.sleep(3 seconds)
      deliveryIO.unsafeRunSync()

      val log = txnLogDAO.getByTxnIds(NonEmptyList(txn1.id, List(txn2.id))).unsafeRunSync()
      log.map(_.txnId).toSet shouldEqual Set(txn1.id, txn2.id)
    }

    "change and process transactions" in {
      // first txns update
      val txn1Change1 = txn1.copy(id = RandomValueGenerator.randomUUID, status = Available)
      val txn2Change1 = txn2.copy(id = RandomValueGenerator.randomUUID, purchaseAmount = Money(randomAmount(2000)), status = Available)
      tprod.submit(TEST_CUSTOMER_NAME, txn1Change1 :: txn2Change1 :: Nil).unsafeRunSync()
      Thread.sleep((3 seconds).toMillis)
      // if a txn with (network + reference) already exists - we update it contents, but not its id
      val existingInDB1 = findQueueWithIds(NonEmptyList(txn1Change1.id, txn2Change1.id :: txn1.id :: txn2.id :: Nil)).unsafeRunSync
      existingInDB1 shouldEqual Set(txn1.id, txn2.id)

      // second txns update
      val txn1Change2 = txn1Change1.copy(status = Rejected, purchaseAmount = Money(randomAmount(2000)))
      val txn2Change2 = txn2Change1.copy(purchaseAmount = txn2.purchaseAmount, status = Paid)
      tprod.submit(TEST_CUSTOMER_NAME, txn1Change2 :: txn2Change2 :: Nil).unsafeRunSync()
      Thread.sleep((3 seconds).toMillis)
      val existingInDB2 = findQueueWithIds(NonEmptyList(txn1Change2.id, txn2Change2.id :: txn1.id :: txn2.id :: Nil)).unsafeRunSync
      existingInDB2 shouldEqual Set(txn1.id, txn2.id)

      // checking change log for txn1
      val changesTxn1 = txnChangeDAO.find(txn1.id).transact(xar.xar).unsafeRunSync().sortBy(_.whenCreated)
      changesTxn1.size shouldBe 2
      val expectedChanges1txn1 = TestStatusChanges(txn1.status).asJson
      val expectedChanges2txn1 = TestStatusAndAmountChanges(txn1Change1.status, txn1Change1.purchaseAmount.amount).asJson
      changesTxn1.map(_.diff).toSet shouldEqual Set(expectedChanges1txn1, expectedChanges2txn1)

      // checking change log for txn2
      val changesTxn2 = txnChangeDAO.find(txn2.id).transact(xar.xar).unsafeRunSync().sortBy(_.whenCreated)
      changesTxn2.size shouldBe 2
      val expectedChanges1txn2 = TestStatusAndAmountChanges(txn2.status, txn2.purchaseAmount.amount).asJson
      val expectedChanges2txn2 = TestStatusAndAmountChanges(txn2Change1.status, txn2Change1.purchaseAmount.amount).asJson
      changesTxn2.map(_.diff).toSet shouldEqual Set(expectedChanges1txn2, expectedChanges2txn2)

      // preparing delivery set
      val txnToDeliveryList = dlvService.composeDeliverySet(FUUID.randomFUUID[IO].unsafeRunSync(), customer.name, Instant.now).unsafeRunSync()
      txnToDeliveryList.size shouldBe 2
      val txn1ToDelivery = txnToDeliveryList.find(_.id == txn1.id).get
      val txn2ToDelivery = txnToDeliveryList.find(_.id == txn2.id).get

      // checking change set for txn1
      txn1ToDelivery.id shouldBe txn1.id
      txn1ToDelivery.status shouldBe txn1Change2.status
      txn1ToDelivery.purchaseAmount shouldBe txn1Change2.purchaseAmount
      val expectedFinalTxn1 = expectedChanges2txn1 deepMerge expectedChanges1txn1
      txn1ToDelivery.diff.get shouldEqual expectedFinalTxn1

      // checking change set for txn2
      txn2ToDelivery.id shouldBe txn2.id
      txn2ToDelivery.status shouldBe txn2Change2.status
      txn2ToDelivery.purchaseAmount shouldBe txn2Change2.purchaseAmount
      val expectedFinalTxn2 = expectedChanges2txn2 deepMerge expectedChanges1txn2
      txn2ToDelivery.diff.get shouldEqual expectedFinalTxn2
    }

    "deliver different changesets" in {

      cleanDB
      setupCustomer()

      // 1. Process txn for the first time. New row in delivery log expected. Diff should be empty.
      tprod.submit(TEST_CUSTOMER_NAME, txn1 :: Nil).unsafeRunSync()
      Thread.sleep((3 seconds).toMillis)
      dlvService.deliver0(customer).unsafeRunSync()
      val log1 = txnLogDAO.getByTxnIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync()
      log1.size shouldBe 1
      log1.head.diff shouldBe empty

      // 2. Process the same txn. No changes expected
      tprod.submit(TEST_CUSTOMER_NAME, txn1 :: Nil).unsafeRunSync()
      Thread.sleep((3 seconds).toMillis)
      dlvService.deliver0(customer).unsafeRunSync()
      val log2 = txnLogDAO.getByTxnIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync()
      log2 shouldEqual log1

      // 3. Update txn. New row in delivery log expected. Diff should be defined.
      val ch1 = txn1.copy(purchaseAmount = Money(randomAmount(1000)))
      tprod.submit(TEST_CUSTOMER_NAME, ch1 :: Nil).unsafeRunSync()
      Thread.sleep((3 seconds).toMillis)
      dlvService.deliver0(customer).unsafeRunSync()
      val log3 = txnLogDAO.getByTxnIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync()
      log3.size shouldBe 2
      log3.maxBy(_.whenDelivered).diff.get shouldBe TestAmountChanges(txn1.purchaseAmount.amount).asJson

      // 4. Update txn with two opposite changes. No changes expected.
      val ch2 = txn1.copy(purchaseAmount = Money(randomAmount(1000)))
      val ch3 = txn1.copy(purchaseAmount = ch1.purchaseAmount)
      tprod.submit(TEST_CUSTOMER_NAME, ch2 :: Nil).unsafeRunSync()
      Thread.sleep((3 seconds).toMillis)
      tprod.submit(TEST_CUSTOMER_NAME, ch3 :: Nil).unsafeRunSync()
      Thread.sleep((3 seconds).toMillis)
      dlvService.deliver0(customer).unsafeRunSync()
      txnLogDAO.getByTxnIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync().size shouldBe 2
      txnQueueDAO.findByIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync() shouldBe empty
    }

    "successful and unsuccessful deliveries" in {
      // successful delivery
      cleanDB
      setupCustomer()

      tprod.submit(TEST_CUSTOMER_NAME, txn1 :: Nil).unsafeRunSync()
      Thread.sleep((2 seconds).toMillis)
      txnQueueDAO.findByIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync().size shouldBe 1
      dlvService.deliver0(customer).unsafeRunSync()
      txnQueueDAO.findByIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync().size shouldBe 0
      txnLogDAO.getByTxnIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync().size shouldBe 1

      // unsuccessful delivery and retry
      cleanDB
      val custRetry = customer.copy(webhookUrl = Some(retryWebhook))
      setupCustomer( custRetry )

      tprod.submit(TEST_CUSTOMER_NAME, txn1 :: Nil).unsafeRunSync()
      Thread.sleep((2 seconds).toMillis)
      val queueBefore = txnQueueDAO.findByIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync()
      queueBefore.size shouldBe 1
      queueBefore.head.attemptCount shouldBe 0
      queueBefore.head.lastAttemptOutcome shouldBe None
      dlvService.deliver0(custRetry).unsafeRunSync()
      val queueAfter = txnQueueDAO.findByIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync()
      queueAfter.size shouldBe 1
      queueAfter.head.attemptCount shouldBe 1
      queueAfter.head.lastAttemptOutcome shouldBe Some("500")
      txnLogDAO.getByTxnIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync().size shouldBe 0
      customerDAO.getByKey(customer.apiKey).unsafeRunSync().get.webhookActive shouldBe true

      // unsuccessful delivery and deactivate webhook
      cleanDB
      val custReject = customer.copy(webhookUrl = Some(rejectWebhook))
      setupCustomer( custReject )
      tprod.submit(TEST_CUSTOMER_NAME, txn1 :: Nil).unsafeRunSync()
      Thread.sleep((2 seconds).toMillis)
      txnQueueDAO.findByIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync().size shouldBe 1
      dlvService.deliver0(custReject).unsafeRunSync()
      val queue = txnQueueDAO.findByIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync()
      queue.size shouldBe 1
      queue.head.lastAttemptOutcome shouldBe Some("400")
      customerDAO.getByKey(customer.apiKey).unsafeRunSync().get.webhookActive shouldBe false
      txnLogDAO.getByTxnIds(NonEmptyList(txn1.id, Nil)).unsafeRunSync().size shouldBe 0
    }
  }

  private def findQueueWithIds(ids: NonEmptyList[FUUID]): IO[Set[FUUID]] = {
    txnQueueDAO.findByIds(ids).map(_.map(_.txnId).toSet)
  }

  private def buildServer: Resource[IO, Server[IO]] = {
    val routes = HttpRoutes.of[IO] {
      case POST -> Root / "transaction" / code =>
        IO(Response(Status(code.toInt)))
    }
    val router = Router("" -> routes).orNotFound
    BlazeServerBuilder[IO].bindHttp(8080, "0.0.0.0")
      .withHttpApp(router)
      .withNio2(true)
      .resource
  }

  private def buildClient: Resource[IO, Client[IO]] = {
    BlazeClientBuilder[IO](Executors.miscExec)
      .withMaxTotalConnections(2)
      .resource
  }

  private def genEvent: EventLogItem = {
    EventLogItem(None, Instant.now, EventLogObjectType.CashbackTxn, Some("id"), None, "message", None)
  }


  private def genTxn: CashbackTransaction = {
    val now = Instant.now.minusSeconds(randomInt(60*60*24))
    CashbackTransaction(
      id = randomUUID
    , userId = randomString
    , customerName = TEST_CUSTOMER_NAME
    , reference = randomString
    , merchantName = "7-eleven"
    , merchantNetwork = TEST_NETWORK_NAME
    , description = None
    , whenCreated = now
    , whenUpdated = now
    , whenClaimed = None
    , whenSettled = None
    , whenPosted = None
    , purchaseDate = now
    , purchaseAmount = Money(randomAmount(1000))
    , purchaseCurrency = "USD"
    , cashbackBaseUSD = Money(randomAmount(1000))
    , cashbackTotalUSD = Money(randomAmount(1000))
    , cashbackUserUSD = Money(randomAmount(1000))
    , cashbackOwnUSD = Money(randomAmount(1000))
    , status = CashbackTxnStatus.Pending
    , parentTxn = None
    , payoutId = None
    , failedReason = None
    , rawTxn = Json.fromFields(Nil)
    , offerId = None
    , offerTimestamp = None
    )
  }


  implicit val tcsEecoder = Encoder[TestStatusChanges]
  implicit val tcaEecoder = Encoder[TestAmountChanges]
  implicit val tcsaEecoder = Encoder[TestStatusAndAmountChanges]
  sealed case class TestStatusChanges(status: CashbackTxnStatus)
  sealed case class TestAmountChanges(purchaseAmount: BigDecimal)
  sealed case class TestStatusAndAmountChanges(status: CashbackTxnStatus, purchaseAmount: BigDecimal)

}
