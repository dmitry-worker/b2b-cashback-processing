package com.noproject.domain.dao.partner

import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.partner.Network
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import doobie.implicits._

class NetworkDAOTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers with BeforeAndAfterAll {

  val dao = new NetworkDAO(xar)
  val networks = (0 until 10).map(_ => genNetwork).toList

  "NetworkDAOTest" should {

    "perform basic operations" in {
      val cio = for {
        _ <- dao.deleteAllTxn()
        c <- dao.insertTxn(networks)
        a <- dao.findAllTxn
        d <- dao.deleteAllTxn()
      } yield (c, a, d)

      val (created, all, deleted) = cio.transact(rollbackTransactor).unsafeRunSync

      created shouldEqual 10
      networks.toSet shouldEqual all.toSet
      deleted shouldEqual 10
    }
  }
  
  def genNetwork = Network(
    name        = randomString
  , description = randomOptString
  )
}
