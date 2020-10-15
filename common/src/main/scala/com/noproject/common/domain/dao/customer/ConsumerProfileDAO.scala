package com.noproject.common.domain.dao.customer

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.customer.ConsumerProfile
import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.query.Query
import javax.inject.{Inject, Singleton}

import scala.language.implicitConversions

@Singleton
class ConsumerProfileDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override type T = ConsumerProfile

  override val tableName = "consumer_profiles"

  override val keyFieldList = List(
    "hash"
  )

  override val allFieldList = List(
    "hash"
  , "name"
  , "age"
  , "male"
  , "phone"
  , "address"
  , "income_class"
  , "purchases_count"
  , "purchases_amount"
  , "last_purchase_date"
  , "last_purchase_usd_amount"
  )

  def findAll: IO[List[ConsumerProfile]] = super.findAll0

  def insert(profiles: List[ConsumerProfile]): IO[Int] = {
    insert1(profiles)
  }

  def insertTxn(prof: ConsumerProfile): ConnectionIO[Int] = {
    insert1(prof :: Nil)
  }

  def findByHash(hash: String): IO[Option[ConsumerProfile]] = {
    val sql = s"select $allFields from $tableName where hash = ? limit 1"
    Query[String, ConsumerProfile](sql)
      .toQuery0(hash)
      .option
  }

  def findBatchByHashes(hashes: NonEmptyList[String]): IO[List[ConsumerProfile]] = {
    val fr0 = Fragment.const(s"select $allFields from $tableName where")
    val fr1 = Fragments.in(fr"hash", hashes)
    (fr0 ++ fr1).query[ConsumerProfile].to[List]
  }

  def findAllProfilesByCustomer(customerName: String): IO[List[ConsumerProfile]] = {
    val allFieldsWithTablePrefix = allFieldList.map("p."+_).mkString(", ")
    val fr = Fragment.const(
      s"""
         |select $allFieldsWithTablePrefix
         |from $tableName p
         |join customer_users u
         |on p.hash = u.hash
         |where u.customer_name = '$customerName'""".stripMargin)
    fr.query[ConsumerProfile].to[List]
  }

}
