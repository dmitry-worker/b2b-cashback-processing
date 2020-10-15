package com.noproject.domain.dao.customer

import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.model.customer.{AccessRole, Customer, CustomerUtil}
import com.noproject.domain.model.customer.{CustomerSession, Session}
import doobie.free.connection.ConnectionIO
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import doobie.implicits._


class CustomerSessionDAOTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with BeforeAndAfterAll with Matchers {

  val sessionDAO = new SessionDAO(xar)
  val cdao = new CustomerDAO(xar)
  val dao = new CustomerSessionDAO(xar, sessionDAO, cdao)

  val customer = genCustomer

  private def clean: ConnectionIO[Unit] = {
    for {
      _ <- dao.deleteAllTxn()
      _ <- cdao.deleteAllTxn()
      _ <- sessionDAO.deleteAllTxn()
    } yield ()
  }


  "CustomerSessionDAO" should {
    "insert and get by token" in {

      val session = genSession.copy(customerName = Some(customer.name))

      val cio = for {
        _ <- clean
        _ <- cdao.insertTxn(customer :: Nil)
        c <- dao.insertTxn(session)
        f <- dao.getByTokenTxn(session.sessionToken)
      } yield (c, f)


      // Check that the returned customer session is complete
      val (customerSession, foundOption) = cio.transact(rollbackTransactor).unsafeRunSync()
      customerSession.session.sessionId should not be session.sessionId
      customerSession.session.customerName shouldEqual Some( customer.name )
      customerSession.session.customerName shouldEqual session.customerName
      customerSession.session.sessionToken shouldEqual session.sessionToken
      customerSession.session.startedAt shouldEqual session.startedAt
      customerSession.session.expiredAt shouldEqual session.expiredAt
      customerSession.customerRoles shouldEqual customer.role

      // Ensure that the session was properly stored
      val found = foundOption.get
      found.customerName shouldEqual session.customerName
      found.session.sessionToken shouldEqual session.sessionToken
      found.customerRoles shouldEqual customer.role
      found.session.startedAt shouldEqual session.startedAt
      found.session.expiredAt shouldEqual session.expiredAt

      // Check that the insertion returned the id as well.
      found.session.sessionId shouldEqual customerSession.session.sessionId
    }

    "should have proper equality" in {
      val basicSession = genSession
      val session1 = CustomerSession( basicSession, Set( AccessRole.Customer ) )
      val session2 = CustomerSession( basicSession, Set( AccessRole.Admin ) )

      session1 should not equal session2
    }

  }

  val secret = "apiSecret"

  def genCustomer = {
    val name = s"Customer" + randomString
    val key = "apiKey" + randomString
    val hash = CustomerUtil.calculateHash(name, key, secret).unsafeRunSync()
    Customer(name, key, hash, Set(AccessRole.Customer), None, None)
  }

  def genSession: Session = Generator[Session].apply
}
