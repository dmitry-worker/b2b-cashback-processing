package com.noproject.common.domain.dao.transaction

import java.time.Clock

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.transaction.{TxnDeliveryLogRow, TxnLastDelivery}
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.fragment.Fragment
import io.chrisdavenport.fuuid.FUUID
import javax.inject.Inject

class TxnDeliveryLogDAO @Inject()(protected val sp: DefaultPersistence, clock: Clock) extends IDAO {

  override type T = TxnDeliveryLogRow

  override val tableName = "txn_delivery_log"

  override val keyFieldList = List(
    "txn_id"
  , "batch_id"
  )

  override val allFieldList = List(
    "txn_id"
  , "batch_id"
  , "customer_name"
  , "when_delivered"
  , "diff"
  , "failure_reason"
  )

  def insertTxn(records: List[TxnDeliveryLogRow]): ConnectionIO[Int] = {
    insert0(records)
  }

  def getByTxnIds(ids: NonEmptyList[FUUID]): IO[List[TxnDeliveryLogRow]] = {
    val sql = Fragment.const(s"select $allFields from $tableName where")
    val where = Fragments.in(fr"txn_id", ids)
    (sql ++ where).query[TxnDeliveryLogRow].to[List]
  }

  def getLastDeliveryTimesByTxnIds(ids: NonEmptyList[FUUID]): ConnectionIO[List[TxnLastDelivery]] = {
    val select = Fragment.const(s"select txn_id, max(when_delivered) from $tableName where")
    val where = Fragments.in(fr"txn_id", ids)
    val group = Fragment.const("group by txn_id")
    val result = select ++ where ++ group
    result.query[TxnLastDelivery].to[List]
  }
}
