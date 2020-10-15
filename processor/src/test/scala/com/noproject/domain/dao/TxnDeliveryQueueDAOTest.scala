package com.noproject.domain.dao

import java.time.{Clock, Instant}

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.partner.{CustomerNetworksDAO, NetworkDAO}
import com.noproject.common.domain.dao.transaction.TxnDeliveryQueueDAO
import com.noproject.common.domain.dao.{CashbackTransactionDAO, DefaultPersistenceTest}
import com.noproject.common.domain.model.customer.Customer
import com.noproject.common.domain.model.partner.Network
import com.noproject.common.domain.model.transaction.{CashbackTransaction, TxnDeliveryQueueRow}
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.fuuid.FUUID
import io.circe.Json
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}


class TxnDeliveryQueueDAOTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers with BeforeAndAfterAll {

  val clock   = Clock.systemUTC

  val dao     = new TxnDeliveryQueueDAO(xar, clock)
  val cdao    = new CustomerDAO(xar)
  val txndao  = new CashbackTransactionDAO(xar)
  val ndao    = new NetworkDAO(xar)
  val cndao   = new CustomerNetworksDAO(xar)

  val customers: List[String] = "customer1" :: "customer2" :: "customer3" :: Nil

  val (sampleTxns, sampleQueue) = (0 until 500).toList.map { _ =>
    val txn  = genTxn(customers)
    val row =  genRow(txn.id, clock.instant(), clock.instant())
    txn -> row
  }.unzip

  private def clean: ConnectionIO[Unit] = {
    for {
      _ <- dao.deleteAllTxn()
      _ <- cndao.deleteAllTxn()
      _ <- txndao.deleteAllTxn()
      _ <- cdao.deleteAllTxn()
      _ <- ndao.deleteAllTxn()
    } yield ()
  }

  "TxnDeliveryQueueDAOTest" should {

    "prepareAndUpdateRecords" in {
      val cio = for {
        _        <- clean
        _        <- cdao.insertTxn(customers.map(createCustomer))
        _        <- ndao.insertTxn(customers.map(c => createNetwork(c + "_network")))
        _        <- cndao.insertTxn(NonEmptyList.fromListUnsafe( customers.map(c => c -> s"${c}_network")) )
        _        <- txndao.upsertTxn(sampleTxns, clock.instant())
        _        <- dao.insertTxn(sampleQueue)
        fuuid    =  FUUID.randomFUUID[IO].unsafeRunSync()
        updated  <- dao.prepareAndUpdateRecords(fuuid, 5, "customer1", clock.instant)
        persList <- dao.findBatchTxn(fuuid)
        pers     = persList.map(_.txnId).toSet
      } yield updated -> pers

      val (updated, persisting) = cio.transact(rollbackTransactor).unsafeRunSync

      updated.length should be > 0
      updated.toSet shouldEqual persisting
    }

  }

  def networkGen: Generator[Network] = Generator[Network]
  def createNetwork( networkName: String ) : Network = networkGen.apply.copy(
    name = networkName
  )

  def custGen: Generator[Customer] = Generator[Customer]
  def createCustomer(name: String): Customer = custGen.apply.copy(
    name = name
  , webhookUrl = Some(s"http://${name}.com")
  , webhookKey = Some(s"${name}.api.key")
  )


  implicit def genJson:Generator[Json] = new Generator[Json] {
    override def apply: Json = Json.fromFields(
      List("fieldName" -> Json.fromString("fieldValue"))
    )
  }

  def txnGen: Generator[CashbackTransaction] = Generator[CashbackTransaction]
  def genTxn(customers: List[String]): CashbackTransaction = {
    txnGen.apply.copy(
      id = randomUUID
    , customerName = randomOneOf( customers )
    , merchantNetwork = randomOneOf( customers ) + "_network"
    )
  }

  def genRow(txnid: FUUID, whenFirst: Instant, whenNext: Instant): TxnDeliveryQueueRow = {
    TxnDeliveryQueueRow(
      txnId              = txnid
    , customerName       = randomOneOf(customers)
    , whenCreated        = whenFirst
    , whenNextAttempt    = whenNext
    , whenLastAttempt    = None
    , lastAttemptOutcome = None
    , attemptCount       = 0
    , batchId            = None
    )
  }



}
