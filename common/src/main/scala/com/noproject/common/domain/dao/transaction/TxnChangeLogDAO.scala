package com.noproject.common.domain.dao.transaction

import java.time.{Clock, Instant}

import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.transaction.TxnChangeLogRow
import doobie.implicits._
import doobie.{ConnectionIO, Fragment, Fragments}
import io.chrisdavenport.fuuid.FUUID
import javax.inject.Inject

class TxnChangeLogDAO @Inject()(protected val sp: DefaultPersistence, clock: Clock) extends IDAO {

  override type T = TxnChangeLogRow

  override val tableName = "txn_change_log"

  override val keyFieldList = List(
    "txn_id"
  )

  override val allFieldList = List(
    "txn_id"
  , "customer_name"
  , "when_created"
  , "diff"
  )

  def insertTxn(records: List[TxnChangeLogRow]): ConnectionIO[Int] = {
    insert1(records)
  }

  def find(txnId: FUUID, from: Option[Instant] = None, to: Option[Instant] = None): ConnectionIO[List[TxnChangeLogRow]] = {
    val fselect = Fragment.const(s"select $allFields from $tableName")
    val ffrom   = from.map ( s => fr"when_created > $from"  )
    val fto     = to.map   ( s => fr"when_created < $to"    )
    val fid     = Some     (      fr"txn_id       = $txnId" )
    val sql     = fselect ++ Fragments.whereAndOpt(ffrom, fto, fid)
    sql.query[TxnChangeLogRow].to[List]
  }
}
