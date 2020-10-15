package com.noproject.common.domain.dao.customer

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.customer.Consumer
import doobie.util.fragment.Fragment
import doobie.util.query.Query
import doobie.util.update.Update
import doobie._
import doobie.implicits._
import javax.inject.{Inject, Singleton}

import scala.language.implicitConversions

@Singleton
class ConsumerDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {



  override type T = Consumer

  override val tableName = "customer_users"

  override val keyFieldList = List(
    "customer_name",
    "user_id"
  )

  override val allFieldList = List(
     "customer_name",
     "user_id",
     "hash"
  )

  def findAll: IO[List[Consumer]] = super.findAll0
  def findAllTxn: ConnectionIO[List[Consumer]] = super.findAll0

  def insert(consumers: List[Consumer]): IO[Int] = {
    insert1(consumers)
  }

  def insertTxn(cons: Consumer): ConnectionIO[Int] = {
    insert1(cons :: Nil)
  }

  def findByHash(hash: String): IO[Option[Consumer]] = {
    val sql = s"select $allFields from $tableName where hash = ? limit 1"
    Query[String, Consumer](sql)
      .toQuery0(hash)
      .option
  }

  def findBatchByHashes(hashes: NonEmptyList[String]): IO[List[Consumer]] = {
    val fr0 = Fragment.const(s"select $allFields from $tableName where")
    val fr1 = Fragments.in(fr"hash", hashes)
    (fr0 ++ fr1).query[Consumer].to[List]
  }

}
