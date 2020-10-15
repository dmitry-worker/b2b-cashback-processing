package com.noproject.domain.dao.customer

import java.time.Instant

import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.domain.model.customer.{AccessRole, Customer, CustomerUtil}
import com.noproject.domain.model.customer.Session
import doobie.free.connection.ConnectionIO
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import doobie.implicits._
import cats.implicits._

class SessionDAOTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with BeforeAndAfterAll with Matchers {

  val dao = new SessionDAO(xar)
  val cdao = new CustomerDAO(xar)

  val customer: Customer = genCustomer

  private def clean: ConnectionIO[Unit] = {
    for {
      _ <- dao.deleteAllTxn()
      _ <- cdao.deleteAllTxn()
    } yield ()
  }



  "SessionDAOTest" should {

    "insert and get by token" in {
      val session = genSession.copy(customerName = Some(customer.name))

      val cio = for {
        _ <- clean
        _ <- cdao.insertTxn(customer :: Nil)
        _ <- dao.insertTxn(session)
        f <- dao.getByTokenTxn(session.sessionToken)
      } yield f


      val found = cio.transact(rollbackTransactor).unsafeRunSync().get

      found.customerName shouldEqual session.customerName
      found.sessionToken shouldEqual session.sessionToken
      found.startedAt shouldEqual session.startedAt
      found.expiredAt shouldEqual session.expiredAt
    }

    "expire session" in {
      val clock = TestClock.apply
      val moment = clock.instant()
      val moment2 = clock.instant().plusMillis(2000)
      val session = genSession.copy(customerName = Some(customer.name), expiredAt = moment)
      Thread.sleep(2000)
      val cio = for {
        _ <- clean
        _ <- cdao.insertTxn(customer :: Nil)
        _ <- dao.insertTxn(session)
        f <- dao.getByTokenTxn(session.sessionToken)
        _ <- dao.updateExpirationByTokenTxn(session.sessionToken, moment2)
        e <- dao.getByTokenTxn(session.sessionToken)
      } yield (f, e)


      val (found,expired) = cio.transact(rollbackTransactor).unsafeRunSync()
      found.get.expiredAt shouldEqual moment
      expired.get.expiredAt shouldEqual moment2
    }
  }

  val secret = "apiKey"

  def genCustomer = {
    val name = s"Customer" + randomString
    val key = "apiKey" + randomString
    val hash = CustomerUtil.calculateHash(name, key, secret).unsafeRunSync()
    Customer(name, key, hash, Set(AccessRole.Customer), None, None)
  }

  def genSession = Generator[Session].apply
}
