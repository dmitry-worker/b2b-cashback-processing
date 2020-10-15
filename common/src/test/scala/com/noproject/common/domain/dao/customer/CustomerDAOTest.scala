package com.noproject.common.domain.dao.customer

import cats.implicits._
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.model.customer.{AccessRole, Customer, CustomerUtil}
import doobie.implicits._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class CustomerDAOTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers with BeforeAndAfterAll {

  val dao = new CustomerDAO(xar)
  val secret = "apiSecret"
  val adminSecret = "adminSecret"

  override def beforeAll(): Unit = {
    super.beforeAll()
    val io = for {
      _ <- dao.deleteAll()
    } yield ()

    io.unsafeRunSync()
  }

  override def afterAll(): Unit = {
    val io = for {
      _ <- dao.deleteAll()
    } yield ()
    io.unsafeRunSync()
    super.afterAll()
  }

  "CustomerDaoTest" should {

    "insert and get by key" in {
      val customers = Seq(genCustomer, genAdmin)
      customers.foreach { customer =>
        val cio = for {
          i <- dao.insertTxn(customer :: Nil)
          r <- dao.getByKeyTxn(customer.apiKey)
        } yield i -> r
        val (count, res) = cio.transact(rollbackTransactor).unsafeRunSync()
        count shouldEqual 1
        res shouldBe defined
        res.get.name shouldBe customer.name
        res.get.apiKey shouldBe customer.apiKey
        res.get.hash shouldBe customer.hash
        res.get.role shouldBe customer.role
      }
    }

    "create and check hash" in {
      val cust1 = genCustomer
      val cust2 = genCustomer
      cust1.hash == cust2.hash shouldBe false
      val check1 = CustomerUtil.checkHash(cust1.name, cust1.apiKey, secret, cust1.hash).unsafeRunSync()
      check1 shouldBe true
      val check2 = CustomerUtil.checkHash(cust2.name, cust2.apiKey, secret, cust2.hash).unsafeRunSync()
      check2 shouldBe true
    }

    "delete users" in {
      val customer = genCustomer
      val cio = for {
        c <- dao.insertTxn( customer :: Nil )
        d <- dao.deleteTxn( customer.apiKey )
        g <- dao.getByKeyTxn( customer.apiKey )
      } yield (c, d, g)
      val (create, delete, get) = cio.transact(rollbackTransactor).unsafeRunSync()
      create shouldEqual 1
      delete shouldEqual 1
      get shouldBe None
    }

    "undelete users" in {
      val customer = genCustomer
      val cio = for {
        c <- dao.insertTxn( customer :: Nil )
        d <- dao.deleteTxn( customer.apiKey )
        u <- dao.undeleteTxn( customer.apiKey )
        g <- dao.getByKeyTxn( customer.apiKey )
      } yield (c, d, u, g)
      val (create, delete, undelete, get) = cio.transact(rollbackTransactor).unsafeRunSync()
      create shouldEqual 1
      delete shouldEqual 1
      undelete shouldEqual 1
      get.get.name shouldEqual customer.name
    }

    "modify roles of a user by API key" in {
      val customer = genCustomer.copy( role = Set( AccessRole.Admin ) )
      val cio = for {
        c <- dao.insertTxn( customer :: Nil )
        u <- dao.updateRoles( customer.apiKey, Set( AccessRole.Customer ) )
        g <- dao.getByKeyTxn( customer.apiKey )
      } yield (c, u, g)
      val (create, update, get) = cio.transact(rollbackTransactor).unsafeRunSync()
      get.get.role shouldBe Set( AccessRole.Customer )
    }

    "indicates proper webhook status" in {
      val customers = List(
        genCustomer.copy(
          webhookUrl = Some( "https://127.0.0.1/ignore" ),
          webhookKey = Some( "goodkey" ),
          webhookActive = true ),
        genCustomer.copy(
          webhookUrl = Some( "https://127.0.0.1/ignore" ),
          webhookKey = Some( "badkey" ),
          webhookActive = false ),
        genCustomer.copy(
          webhookUrl = None,
          webhookKey = None,
          webhookActive = false ) )

      val expected = customers.map( _.webhookActive )

      val cio = for {
        i <- dao.insertTxn( customers )
        g <- dao.webhookActive(customers(0).name)
        b <- dao.webhookActive(customers(1).name)
        n <- dao.webhookActive(customers(2).name)
      } yield (i, g, b, n)

      val (create, good, bad, none) = cio.transact(rollbackTransactor).unsafeRunSync()
      create shouldEqual 3
      good shouldEqual true
      bad  shouldEqual false
      none shouldEqual false
    }

  }

  def genCustomer: Customer = {
    val name = s"Customer" + randomString
    val key = "apiKey" + randomString
    val hash = CustomerUtil.calculateHash(name, key, secret).unsafeRunSync()
    val hookUrl : Option[String] = randomOptString.map( x => "https://127.0.0.1/ignore/" + x )
    val hookKey : Option[String] = hookUrl.map( _ => randomString )
    val hookActive : Boolean = hookUrl.exists( _ => randomBoolean )
    Customer(name, key, hash, Set(AccessRole.Customer), hookUrl, hookKey, active = true, hookActive )
  }

  def genAdmin: Customer = {
    val name = s"Admin" + randomString
    val key = "adminKey" + randomString
    val hash = CustomerUtil.calculateHash(name, key, adminSecret).unsafeRunSync()
    Customer(name, key, hash, Set(AccessRole.Admin, AccessRole.Customer), None, None)
  }
}
