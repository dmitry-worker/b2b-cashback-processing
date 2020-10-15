package com.noproject.domain.dao.partner

import cats.data.NonEmptyList
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.partner.{CustomerNetworksDAO, NetworkDAO}
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.customer.{AccessRole, Customer}
import com.noproject.common.domain.model.partner.Network
import doobie.free.connection.ConnectionIO
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import doobie.implicits._

class CustomerNetworkDAOTest extends WordSpec with BeforeAndAfterAll with DefaultPersistenceTest with RandomValueGenerator with Matchers {

  val ndao = new NetworkDAO(xar)
  val cdao = new CustomerDAO(xar)
  val cndao = new CustomerNetworksDAO(xar)
  val networks = (0 until 10).map(_ => genNetwork).toList
  val customer1 = Customer(randomString, randomString, randomString, Set(AccessRole.Noaccess), None, None, webhookActive = false )
  val customer2 = Customer(randomString, randomString, randomString, Set(AccessRole.Noaccess), None, None, webhookActive = false )

  private def clean: ConnectionIO[Unit] = {
    for {
      _ <- cndao.deleteAllTxn()
      _ <- ndao.deleteAllTxn()
      _ <- cdao.deleteAllTxn()
    } yield ()
  }

  "CustomerNetworkDAOTest" should {

    "find network for customer" in {

      // 2 merchants fot customer 1
      val c1nets = networks.slice(1,3)
      // 3 merchants for customer 2
      val c2nets = networks.slice(2,5)

      val cio = for {
        _ <- clean
        _ <- cdao.insertTxn(customer1 ::  customer2 :: Nil)
        _ <- ndao.insertTxn(networks)
        _ <- cndao.insert(customer1.name, NonEmptyList.fromListUnsafe(c1nets.map(_.name)))
        _ <- cndao.insert(customer2.name, NonEmptyList.fromListUnsafe(c2nets.map(_.name)))
        res1 <- cndao.getByCustomerNameTxn(customer1.name)
        res2 <- cndao.getByCustomerNameTxn(customer2.name)
      } yield (res1, res2)

      val (res1, res2) = cio.transact(rollbackTransactor).unsafeRunSync
      res1.size shouldBe networks.size
      res1.count(_.enabled) shouldBe 2
      res1.filter(_.enabled).map(i => Network(i.name, i.description)).toSet shouldEqual c1nets.toSet

      res2.size shouldBe networks.size
      res2.count(_.enabled) shouldBe 3
      res2.filter(_.enabled).map(i => Network(i.name, i.description)).toSet shouldEqual c2nets.toSet

      cndao.deleteAll().unsafeRunSync()
    }

    "update customer networks" in {
      // val exNets = ndao.findAll.unsafeRunSync()
      val cNets = networks.slice(1,5)
      val dNets = cNets.slice(2,5)

      val cio = for {
        _ <- clean
        _ <- cdao.insertTxn(customer1 ::  customer2 :: Nil)
        _ <- ndao.insertTxn(networks)
        _ <- cndao.insert(customer1.name, NonEmptyList.fromListUnsafe(cNets.map(_.name)))
        e <- cndao.getNetworkNamesByCustomerNameTxn(customer1.name)
        _ <- cndao.deleteTxn(customer1.name, NonEmptyList.fromListUnsafe(dNets.map(_.name)))
        a <- cndao.getNetworkNamesByCustomerNameTxn(customer1.name)
      } yield (e, a)

      val (exists, after) = cio.transact(rollbackTransactor).unsafeRunSync
      exists.toSet shouldEqual cNets.map(_.name).toSet
      after.toSet shouldEqual cNets.map(_.name).toSet -- dNets.map(_.name).toSet
    }

  }

  def genNetwork = Network(
    name        = randomString
  , description = randomOptString
  )
}
