package com.noproject.common.domain.dao.customer

import cats.data.NonEmptyList
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.model.customer.{Consumer, ConsumerProfile}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class ConsumerProfileDAOTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers with BeforeAndAfterAll {

  val cdao = new ConsumerDAO(xar)
  val dao = new ConsumerProfileDAO(xar)

  val cons = Consumer("x", "y")
  val gen = Generator[ConsumerProfile]
  val cp = gen.apply.copy(hash = cons.hash)

  override protected def beforeAll(): Unit = {
    cdao.insert(cons :: Nil)
  }

  "ConsumerProfileDAOTest" should {

    "insert" in {
      dao.insert(cp :: Nil).unsafeRunSync()
    }

    "findBatchByHashes" in {
      val fetchResult = dao.findBatchByHashes(NonEmptyList(cp.hash, Nil)).unsafeRunSync()
      fetchResult shouldEqual List(cp)
    }

    "findByHash" in {
      val fetchResult = dao.findByHash(cons.hash).unsafeRunSync()
      fetchResult shouldEqual Some(cp)
    }

  }

}
