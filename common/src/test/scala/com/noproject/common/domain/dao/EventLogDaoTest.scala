package com.noproject.common.domain.dao

import java.time.Instant

import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import io.circe.Json
import org.scalatest.{Matchers, WordSpec}
import cats.implicits._
import com.noproject.common.domain.model.Money
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import doobie.implicits._

import scala.math.BigDecimal.RoundingMode

class EventLogDaoTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers {

  private val txns = (0 until  10).map(_ => genTxn()).toList
  private val dao = new EventLogDao(xar)

  "EventLogDaoTest" should {
    "insert event" in {
      val cio = for {
        _ <- dao.deleteAllTxn()
        s <- txns.map(t => dao.insertTxn(t.asEventLogItem("test", None))).sequence
        i  = s.sum
        a <- dao.findAllTxn
        d <- dao.deleteAllTxn()
      } yield (i, a, d)

      val (created, all, deleted) = cio.transact(rollbackTransactor).unsafeRunSync
      created shouldEqual 10
      all.size shouldBe 10
      deleted shouldEqual 10
      val actualSet: Set[String] = all.flatMap(_.objectId).toSet
      val expectedSet: Set[String] = txns.map(_.id).map(_.toString).toSet
      actualSet shouldEqual expectedSet
    }

    "insert multiple" in {
      val txns = (0 until 10).toList.map { _ => genEvent }
      val cio = for {
        _ <- dao.deleteAllTxn()
        i <- dao.insertManyTxn(txns)
        a <- dao.findAllTxn
      } yield (i, a)

      val (created, all) = cio.transact(rollbackTransactor).unsafeRunSync
      created shouldBe 10
      all.size shouldBe 10
      txns.toSet shouldEqual all.map(_.copy(eventId = None)).toSet
    }
  }




  implicit val jsonGen: Generator[Json] = new Generator[Json] { override def apply: Json = Json.Null }
  private val eventGen = Generator[EventLogItem]
  private def genEvent: EventLogItem = eventGen.apply.copy(eventId = None)

  private def genTxn(net: Option[String] = None, cust: Option[String] = None) = {
    val now = Instant.now.minusSeconds(randomInt(60*60*24))
    val amount = randomInt(1000)
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
      , purchaseAmount = Money(amount)
      , purchaseCurrency = "USD"
      , cashbackBaseUSD = Money(BigDecimal(amount * 0.10))
      , cashbackTotalUSD = Money(BigDecimal(amount * 0.10))
      , cashbackUserUSD = Money(BigDecimal(amount * 0.03))
      , cashbackOwnUSD = Money(BigDecimal(amount * 0.07))
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
