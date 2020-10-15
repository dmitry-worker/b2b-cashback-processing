package com.noproject.domain.dao.merchant

import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.merchant.MerchantDAO
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.merchant.MerchantRow
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import doobie.implicits._

class MerchantDAOTest
  extends WordSpec
    with DefaultPersistenceTest
    with RandomValueGenerator
    with Matchers
    with BeforeAndAfterAll {

  val dao = new MerchantDAO(xar)
  val merchants = (0 until 10).map(_ => genMerchant).toList

  "MerchantDAOTest" should {

    "perform basic operations" in {
      val cio = for {
        _ <- dao.deleteAllTxn()
        i <- dao.insertTxn(merchants)
        a <- dao.findAllTxn
        n <- dao.getMerchantByNameTxn(merchants.head.merchantName)
        d <- dao.deleteAllTxn()
      } yield (i, a, n, d)

      val (insert, all, byname, delete) = cio.transact(rollbackTransactor).unsafeRunSync
      insert shouldEqual merchants.size
      delete shouldEqual merchants.size
      merchants.toSet shouldEqual all.toSet
      byname shouldEqual Some(merchants.head)
    }

  }
  
  def genMerchant = MerchantRow(
    merchantName  = randomString
  , description   = randomString
  , logoUrl       = randomString
  , imageUrl      = randomOptString
  , categories    = randomOf("catA" :: "catB" :: "catC" :: Nil).toList
  , priceRange    = randomOptString
  , website       = randomOptString
  , phone         = randomOptString
  )
}
