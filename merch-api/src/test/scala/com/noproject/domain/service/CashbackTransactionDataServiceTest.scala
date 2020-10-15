package com.noproject.domain.service

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.noproject.common.controller.dto.TransactionSearchParams
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.merchant.MerchantDAO
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.domain.dao.{CashbackTransactionDAO, EventLogDao, DefaultPersistenceTest}
import com.noproject.common.data.gen.{RandomValueGenerator, TestClock}
import com.noproject.common.domain.model.merchant.MerchantRow
import com.noproject.common.domain.model.partner.Network
import com.noproject.common.domain.service.{CashbackTransactionDataService, EventLogDataService}
import com.noproject.common.domain.dao.partner.CustomerNetworksDAO
import com.noproject.common.domain.dao.transaction.{TxnChangeLogDAO, TxnDeliveryQueueDAO}
import com.noproject.common.domain.model.Money
import com.noproject.common.domain.model.customer.{AccessRole, Customer, CustomerUtil}
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import io.circe.Json
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, Matchers, WordSpec}

import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode

@DoNotDiscover
class CashbackTransactionDataServiceTest extends WordSpec
  with BeforeAndAfterAll
  with DefaultPersistenceTest
  with RandomValueGenerator
  with Matchers {

  private val clock = TestClock.apply

  val cDao = new CustomerDAO(xar)
  val nDao = new NetworkDAO(xar)
  val mDao = new MerchantDAO(xar)
  val cnDao = new CustomerNetworksDAO(xar)
  val cbDao = new CashbackTransactionDAO(xar)
  val elDao = new EventLogDao(xar)
  val elDs = new EventLogDataService(elDao)
  val cbDs = new CashbackTransactionDataService(
      sp     = xar
    , dqDAO  = new TxnDeliveryQueueDAO(xar, clock) //TxnDeliveryQueueDAO
    , logDAO = new TxnChangeLogDAO(xar, clock) //TxnChangeLogDAO
    , cnDAO  = cnDao //CustomerNetworksDAO
    , cDAO   = cDao //CustomerDAO
    , dao    = cbDao //CashbackTransactionDAO
    , clock  = clock //Clock
  )

  private def clean: ConnectionIO[Unit] = {
    for {
      _ <- elDao.deleteAllTxn()
      _ <- cnDao.deleteAllTxn()
      _ <- cbDao.deleteAllTxn()
      _ <- cDao.deleteAllTxn()
      _ <- nDao.deleteAllTxn()
      _ <- mDao.deleteAllTxn()
    } yield ()
  }

  override protected def beforeAll(): Unit = {
    // should exist because of FK
    super.beforeAll()
    clean
  }

  override protected def afterAll(): Unit = {
    clean
    super.afterAll()
  }

  "CashbackTransactionDataServiceTest" should {

    "insert valid txn" in {
      val now = clock.instant()
      val cio = for {
        _     <- clean // <- this is important for thread-safe testing
        txn   <- prepareProperDataAndCreateTxn
        upd   <- ().pure[ConnectionIO] // cbDs.createOrUpdateTxn(txn, now) // TODO
        found <- cbDao.findTxn(TransactionSearchParams())
      } yield (txn,found)
      val io = cio.transact(rollbackTransactor)
      io.redeem(
        th  => fail,
        res => IO.unit // res._2.head.reference shouldBe res._1.reference // TODO
      ).unsafeRunSync()
    }

    "try to insert txn, catch purchaseAmount failure and log error" in {
      failuredInsert(corruptTxnAmount)
    }

    "try to insert txn, catch purchaseDate failure #1 and log error" in {
      failuredInsert(corruptTxnDate1)
    }

    "try to insert txn, catch purchaseDate failure #2 and log error" in {
      failuredInsert(corruptTxnDate2)
    }

//    "update valid txn" in {
//      val cio = for {
//        _     <- clean // <- this is important for thread-safe testing
//        txn   <- prepareProperDataAndCreateTxn
//        upd   <- cbDs.createOrUpdateTxn(txn)
//        upTxn  = txn.copy(status = CashbackTxnStatus.Paid)
//        upd   <- cbDs.createOrUpdateTxn(upTxn)
//        found <- cbDao.findTxn(TransactionSearchParams())
//      } yield (upTxn,found)
//
//      val io = cio.transact(rollbackTransactor)
//      io.redeem(
//        th  => fail,
//        res => {
//          res._2.head.reference shouldBe res._1.reference
//          res._2.head.status shouldBe res._1.status
//        }
//      ).unsafeRunSync()
//    }

    "try to update txn, catch status failure" in {
      failuredUpdate(corruptTxnStatus)
    }

    "try to update txn, catch immutable fields failure" in {
      failuredUpdate(corruptTxnMerchant)
    }
  }

  private def corruptTxnAmount(txn: CashbackTransaction): CashbackTransaction =
    txn.copy(purchaseAmount = Money.zero)

  private def corruptTxnDate1(txn: CashbackTransaction): CashbackTransaction =
    txn.copy(purchaseDate = Instant.now.plusSeconds(10.days.toSeconds))

  private def corruptTxnDate2(txn: CashbackTransaction): CashbackTransaction =
    txn.copy(purchaseDate = Instant.now.minusSeconds(1000.days.toSeconds))

  private def corruptTxnStatus(txn: CashbackTransaction): CashbackTransaction =
    txn.copy(status = CashbackTxnStatus.Pending)

  private def corruptTxnMerchant(txn: CashbackTransaction): CashbackTransaction =
    txn.copy(merchantName = randomString)

  private def failuredInsert(corruptionFunc: CashbackTransaction => CashbackTransaction) = {
    clock.step()
    val now = clock.instant()
    val cio = for {
      _   <- clean
      txn <- prepareProperDataAndCreateTxn
      corruptedTxn = corruptionFunc(txn)
      upd <- ().pure[ConnectionIO] //cbDs.createOrUpdateTxn(corruptedTxn, now) // TODO
    } yield ()
    val io = cio.transact(rollbackTransactor)
    io.redeem(
      th  => succeed,
      res => fail
    ).unsafeRunSync()
  }

  private def failuredUpdate(corruptionFunc: CashbackTransaction => CashbackTransaction) = {
    val now = clock.instant()
    val cio = for {
      _     <- clean
      txn   <- prepareProperDataAndCreateTxn
      txnS   = txn.copy(status = CashbackTxnStatus.Available)
      upd   <- ().pure[ConnectionIO] //cbDs.createOrUpdateTxn(txnS, now) // TODO
      txnU   = corruptionFunc(txnS)
      upd   <- ().pure[ConnectionIO] //cbDs.createOrUpdateTxn(txnU, now) // TODO
      found <- cbDao.findTxn(TransactionSearchParams())
    } yield ()

    val io = cio.transact(rollbackTransactor)
    io.redeem(
      th  =>
        succeed,
      res =>
        fail
    ).unsafeRunSync()
  }
  
  def prepareProperDataAndCreateTxn: ConnectionIO[CashbackTransaction] = {
    val customer = genCustomer
    val network = genNetwork
    val merchant = genMerchant
    for {
      cust <- cDao.insertTxn(customer :: Nil)
      net  <- nDao.insertTxn(List(network))
      cnet <- cnDao.insert(customer.name, NonEmptyList(network.name, Nil))
      merc <- mDao.insertTxn(List(merchant))
      txn  =  genTxn(Some(network.name), Some(customer.name), Some(merchant.merchantName))
    } yield txn
  }

  // TODO replace all of this with generic random objects
   def genMerchant = MerchantRow(
     merchantName  = randomString
    , description   = randomString
    , logoUrl       = randomString
    , imageUrl      = randomOptString
    , categories    = randomOf("catA" :: "catB" :: "catC" :: Nil).toList
    , priceRange    = randomOptString
    , website       = randomOptString
    , phone         = randomOptString
  )

  def genNetwork = Network(randomString, randomOptString)

  def genCustomer = {
    val name = s"Customer" + randomString
    val key = "apiKey" + randomString
    val secret = "apiSecret"
    val hash = CustomerUtil.calculateHash(name, key, secret).unsafeRunSync()
    Customer(name, key, hash, Set(AccessRole.Customer), None, None)
  }

  def genTxn(net: Option[String] = None, cust: Option[String] = None, merc: Option[String] = None) = {
    val now = clock.instant().minusSeconds(randomInt(60*60*24))
    val amount = randomMoney(1000)
    CashbackTransaction(
        id = randomUUID
      , userId = randomString
      , customerName = cust.getOrElse(randomString)
      , reference = randomString
      , merchantName = merc.getOrElse(randomString)
      , merchantNetwork = net.getOrElse("azigo")
      , description = None
      , whenCreated = now
      , whenUpdated = now
      , whenClaimed = None
      , whenSettled = None
      , whenPosted = None
      , purchaseDate = now
      , purchaseAmount = amount
      , purchaseCurrency = "USD"
      , cashbackBaseUSD = amount
      , cashbackTotalUSD = amount * 0.10
      , cashbackUserUSD = amount * 0.03
      , cashbackOwnUSD = amount * 0.07
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
