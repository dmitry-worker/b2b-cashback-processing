package com.noproject.common.domain.dao

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.controller.dto.TransactionSearchParams
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.model.transaction.{CashbackTransaction, TxnKey}
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import fs2.Stream
import javax.inject.{Inject, Singleton}
import shapeless.HNil

abstract class TransactionDAO(protected val sp: DefaultPersistence, val tableName: String) extends IDAO {

  override type T = CashbackTransaction

  override def keyFieldList: List[String] = List("id")

  override def allFieldList: List[String] = List(
    "id"
  , "user_id"
  , "customer_name"
  , "reference"
  , "merchant_name"
  , "merchant_network"
  , "description"
  , "when_claimed"
  , "when_settled"
  , "when_posted"
  , "purchase_date"
  , "purchase_amount"
  , "purchase_currency"
  , "cashback_base_usd"
  , "cashback_amount_usd"
  , "cashback_user_usd"
  , "cashback_own_usd"
  , "status"
  , "parent_txn"
  , "payout_id"
  , "failed_reason"
  , "raw_txn"
  , "offer_id"
  , "offer_timestamp"

  // Bookkeeping fields must be at the end.
  , "when_created"
  , "when_updated"
  )

  override def ginField = "search_index_gin"

  val conflictFields = List("reference", "merchant_network")
  val updateFields = List(
    "status"
  , "purchase_amount"
  , "cashback_base_usd"
  , "cashback_amount_usd"
  , "cashback_user_usd"
  , "cashback_own_usd"
  , "when_claimed"
  , "when_settled"
  , "when_posted"
  , "when_updated"
  , "raw_txn"
  )

  def findAll: IO[List[CashbackTransaction]] = super.findAll0
  def findAllTxn: ConnectionIO[ List[ CashbackTransaction ] ] = super.findAll0

  def findAllByRefsAndNetworks(keys: NonEmptyList[TxnKey]): ConnectionIO[Map[TxnKey, CashbackTransaction]] = {
    val fr0 = Fragment.const(s"select $allFields from $tableName")
    val frCond = keys.map(k => fr"(reference = ${k.reference} and merchant_network = ${k.network}) ").toList
    val frWhere = Fragments.whereOr(frCond: _*)
    val sql = fr0 ++ frWhere
    sql.query[CashbackTransaction].to[List].map {
      _.map ( txn => txn.txnKey -> txn ).toMap
    }
  }

  def selectForUpdate(reference: String, network: String): ConnectionIO[Option[CashbackTransaction]] = {
    val sql = s"select $allFields from $tableName where reference = ? and merchant_network = ? for update"
    Query[(String, String), CashbackTransaction](sql, None).toQuery0((reference, network)).option
  }

  def upsert(txns: List[CashbackTransaction], atTime: Instant): IO[Int] = upsertTxn(txns, atTime)

  def upsertTxn(txns: List[CashbackTransaction], atTime: Instant): ConnectionIO[Int] = {
    val valuesWithGin = txns.map(v => v.setNow(atTime) :: v.searchIndex :: HNil)
    upsertGin(valuesWithGin, conflictFields, updateFields)
  }

  def find(tsp: TransactionSearchParams, customerName: Option[String] = None): IO[List[CashbackTransaction]] = {
    findTxn(tsp, customerName)
  }

  def findTxn(tsp: TransactionSearchParams, customerName: Option[String] = None): ConnectionIO[List[CashbackTransaction]] = {
    var result = findQueryFragment(tsp, customerName)
    tsp.limit.foreach { lim => result ++= fr"LIMIT $lim OFFSET ${tsp.offset.getOrElse(0)}" }
    result.query[CashbackTransaction].to[List]
  }

  // maybe do not allow all transactions
  def stream(tsp: TransactionSearchParams, customerName: Option[String]): Stream[IO, CashbackTransaction] = {
    findQueryFragment(tsp, customerName)
      .query[CashbackTransaction]
      .streamWithChunkSize(100)
      .transact(sp.xar)
  }

  def checkCountForDescription(cn: String): IO[Int] = checkCountForDescriptionTxn(cn)

  def checkCountForDescriptionTxn(cn: String): ConnectionIO[Int] = {
    val base = Fragment.const( s"SELECT COUNT(*) FROM $tableName" )
    val pred = fr"WHERE description = $cn"
    (base ++ pred).query[Int].unique
  }

  private def findQueryFragment(tsp: TransactionSearchParams, customerName: Option[String]): Fragment = {
    val query   = customerName match {
      case None =>
        Fragment.const(s"select $allFields from $tableName t")
      case Some(_) =>
        val allFieldsWithTablePrefix = allFieldList.map("t."+_).mkString(", ")
        Fragment.const(
          s"""
             |select $allFieldsWithTablePrefix
             |from $tableName t
             |join customer_networks n
             |on t.merchant_network = n.network_name
             |and t.customer_name = n.customer_name""".stripMargin)
    }

    val fid = tsp.ids.map { rule =>
      if (rule.include) Fragments.in(fr"id", rule.values)
      else              Fragments.notIn(fr"id", rule.values)
    }

    val fmerc   = tsp.merchant.map { s =>
      val tsQuerySource = s.filter(l => l.isLetterOrDigit || l.isWhitespace)
      Fragment.const(s"search_index_gin @@ to_tsquery('english', '''$tsQuerySource''')")
    }

    val fnet    = tsp.network.map { s => fr"merchant_network = $s" }
    val fuser   = tsp.user.map { s => fr"user_id = $s" }
    val fbegin  = tsp.beginsAt.map { s => fr"purchase_date >= $s" }
    val fend    = tsp.endsAt.map { s => fr"purchase_date <= $s" }
    val fstatus = tsp.status.map { s => fr"status = ${s.entryName}" }
    val fcust   = customerName.map { s => fr"t.customer_name = $s" }

    val where = Fragments.whereAndOpt(fid, fmerc, fnet, fuser, fbegin, fend, fstatus, fcust)
    query ++ where ++ fr"ORDER BY purchase_date desc"
  }

}
