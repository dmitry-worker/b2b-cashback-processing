package com.noproject.domain.dao

import java.time.{Clock, Instant, ZoneId}

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.controller.dto.TransactionSearchParams
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.partner.{CustomerNetworksDAO, NetworkDAO}
import com.noproject.common.domain.dao.{CashbackTransactionDAO, DefaultPersistenceTest}
import com.noproject.common.data.gen.{RandomValueGenerator, TestClock}
import com.noproject.common.domain.model.partner.Network
import com.noproject.common.domain.dao.partner.CustomerNetworksDAO
import com.noproject.common.domain.model.customer.{AccessRole, Customer}
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import doobie.free.connection.ConnectionIO
import io.circe.Json
import doobie.implicits._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.math.BigDecimal.RoundingMode

class CashbackTransactionDAOTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers with BeforeAndAfterAll {

  val clock = TestClock.apply

  val dao = new CashbackTransactionDAO(xar)
  val txns = (0 until 10).map(_ => genTxn()).toList

  val netDao  = new NetworkDAO(xar)
  val cnetDao = new CustomerNetworksDAO(xar)
  val custDao = new CustomerDAO(xar)

  private def clean: ConnectionIO[Unit] = {
    for {
      _ <- dao.deleteAllTxn()
      _ <- netDao.deleteAllTxn()
      _ <- cnetDao.deleteAllTxn()
      _ <- custDao.deleteAllTxn()
    } yield ()
  }

  "CashbackTransactionDAO" should {

    "perform basic operations" in {
      val now = clock.instant()
      val txnid = txns.head.id

      val cio = for {
        _ <- clean
        _ <- dao.upsertTxn(txns, now)
        a <- dao.findAllTxn
        i <- dao.findTxn(TransactionSearchParams().withIds(NonEmptyList(txnid, Nil)))
        o <- dao.findTxn(TransactionSearchParams())
        d <- dao.deleteAllTxn
      } yield (a, i, o, d)

      val (all, byid, ordered, deleted) = cio.transact(rollbackTransactor).unsafeRunSync
      all.size shouldEqual txns.size
      all.toSet shouldEqual txns.toSet
      byid should not be empty
      byid.head shouldEqual txns.head
      ordered.size shouldEqual txns.size
      ordered shouldEqual txns.sortBy(_.whenCreated)(Ordering[Instant].reverse)
      deleted shouldEqual txns.size
    }

    "find with customer id" in {
      val apikey = randomString
      val cust = Customer(randomString, apikey, randomString, Set(AccessRole.Noaccess), None, None)

      val network1 = Network(randomString, None)
      val network2 = Network(randomString, None)

      val txns1  = (0 until 10).map(_ => genTxn(Some(network1.name), Some(cust.name))).toList
      val txns2  = (0 until 10).map(_ => genTxn(Some(network2.name), Some(cust.name))).toList

      val findOneParams = TransactionSearchParams().withIds(NonEmptyList(txns1.head.id, Nil))
      val anotherParams = TransactionSearchParams().withIds(NonEmptyList(txns2.head.id, Nil))

      val now = clock.instant()

      val cio = for {
        _ <- clean
        _ <- custDao.insertTxn(cust :: Nil)
        _ <- netDao.insertTxn(List(network1, network2))
        _ <- cnetDao.insert(cust.name, NonEmptyList(network1.name, Nil))
        _ <- dao.upsertTxn(txns1 ++ txns2, now )
        r <- dao.findTxn(TransactionSearchParams(), Some(cust.name))
        o <- dao.findTxn(findOneParams, Some(cust.name))
        a <- dao.findTxn(anotherParams, Some(cust.name))
      } yield (r, o, a)

      val (res, one, another)  = cio.transact(rollbackTransactor).unsafeRunSync

      res.toSet shouldEqual txns1.toSet
      one.size shouldBe 1
      one.head shouldEqual txns1.head
      another.size shouldBe 0
    }
  }
  
  def genTxn(net: Option[String] = None, cust: Option[String] = None): CashbackTransaction = {
    val now = clock.instant
    val amount = randomMoney(1000)
    CashbackTransaction(
      id = randomUUID
    , userId = randomString
    , customerName = cust.getOrElse(randomString)
    , reference = randomString
    , merchantName = randomString
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
