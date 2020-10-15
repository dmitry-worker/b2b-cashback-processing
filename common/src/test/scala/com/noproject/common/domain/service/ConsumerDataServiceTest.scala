package com.noproject.common.domain.service

import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.customer.{ConsumerDAO, ConsumerProfileDAO}
import com.noproject.common.domain.model.customer.{Consumer, ConsumerProfile}
import org.scalatest.{Matchers, WordSpec}

class ConsumerDataServiceTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers {

  private val dao = new ConsumerDAO(xar)
  private val pdao = new ConsumerProfileDAO(xar)
  private val ds = new ConsumerDataService(dao, pdao, xar)

  private val cons = Consumer("x", "y")
  private val prof = ConsumerProfile.instance(cons.hash)


  "ConsumerDataServiceTest" should {

    "insertConsumer" in {
      ds.insertConsumer("x", "y", None).unsafeRunSync
    }

    "getConsumersByCustomer" in {
      val result = ds.getConsumersByCustomer("x").unsafeRunSync()
      result.length shouldEqual 1
      result should contain (prof)
    }

  }


}
