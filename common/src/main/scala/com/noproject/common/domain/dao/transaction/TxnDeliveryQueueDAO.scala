package com.noproject.common.domain.dao.transaction

import java.time.{Clock, Instant}

import cats.data.NonEmptyList
import cats.effect.IO
import cats.free.Free
import cats.implicits._
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.transaction.TxnDeliveryQueueRow
import doobie._
import doobie.implicits._
import io.chrisdavenport.fuuid.FUUID
import javax.inject.{Inject, Singleton}
import shapeless.{::, HNil}

@Singleton
class TxnDeliveryQueueDAO @Inject()(protected val sp: DefaultPersistence, clock: Clock) extends IDAO {

  override type T = TxnDeliveryQueueRow

  override val tableName = "txn_delivery_queue"

  override val keyFieldList = List(
    "txn_id"
  )

  override val allFieldList = List(
    "txn_id"
  , "customer_name"
  , "when_created"
  , "when_next_attempt"
  , "when_last_attempt"
  , "last_attempt_outcome"
  , "attempt_count"
  , "batch_id"
  )

  def insertTxn(records: List[TxnDeliveryQueueRow]): ConnectionIO[Int] = {
    insert1(records)
  }

  def insert(records: List[TxnDeliveryQueueRow]): IO[Int] = {
    insert1(records)
  }

  def findByIds(txnIds: NonEmptyList[FUUID]): IO[List[TxnDeliveryQueueRow]] = {
    val sql = selectAll()
    val where = Fragments.whereAnd( Fragments.in(fr"txn_id", txnIds) )
    (sql ++ where).query[TxnDeliveryQueueRow].to[List]
  }

  def removeFromQueue(fuuid: FUUID): ConnectionIO[Int] = {
    val fr0 = Fragment.const(s"delete from $tableName")
    val fr1 = Fragments.whereAnd(fr"batch_id = $fuuid")
    (fr0 ++ fr1).update.run
  }

  def updateOutcome(fuuid: FUUID, outcome: Option[String]): ConnectionIO[Int] = {
    val sql = s"update $tableName set last_attempt_outcome = ? where batch_id = ?"
    Update[(Option[String], FUUID)](sql).toUpdate0((outcome, fuuid)).run
  }

  // TODO: configurable?
  val defaultIntervalSeconds: Int = 60 // 1 minute
  val maxAttempts: Int = 5

  type VALUES_TYPE = (
       Instant // next
    :: Option[Instant] // last
    :: Int  // count
    :: Option[FUUID]  // batch
    :: FUUID  // id
    :: HNil
  )


  def findBatch(fuuid: FUUID): IO[List[TxnDeliveryQueueRow]] = {
    findBatchTxn(fuuid)
  }

  def findBatchTxn(fuuid: FUUID): ConnectionIO[List[TxnDeliveryQueueRow]] = {
    val fr0 = selectAll()
    val fr1 = Fragments.whereAnd(fr"batch_id = $fuuid")
    (fr0 ++ fr1).query[TxnDeliveryQueueRow].to[List]
  }

  def prepareAndUpdateRecords(batchId: FUUID, batchSize: Int, customerName: String, now: Instant): ConnectionIO[List[FUUID]] = {
    def select0: ConnectionIO[List[TxnDeliveryQueueRow]] = {
      val fr0 = selectAll()
      val fr1 = Fragments.whereAnd(
        fr"when_next_attempt <= $now"
        , fr"customer_name = $customerName"
        , fr"attempt_count < $maxAttempts"
      )
      val fr2 = fr"limit $batchSize for update"
      (fr0 ++ fr1 ++ fr2).query[TxnDeliveryQueueRow].to[List]
    }

    def update0(queue: List[TxnDeliveryQueueRow]): ConnectionIO[Int] = {
      if (queue.isEmpty) Free.pure(0) else update1(queue)
    }

    def update1(queue: List[TxnDeliveryQueueRow]): ConnectionIO[Int] = {
      val values = queue.map { row =>
        val attempts = row.attemptCount + 1
        val nextTime = now.plusSeconds(defaultIntervalSeconds * attempts * attempts)
        nextTime :: Option(now) :: attempts :: Option(batchId) :: row.txnId :: HNil
      }
      val sql =s"""
       |update $tableName
       |  set (when_next_attempt, when_last_attempt, attempt_count, batch_id) = (?, ?, ?, ?)
       |  where txn_id = ?
       |""".stripMargin
      Update[VALUES_TYPE](sql).updateMany(values)
    }

    val result: ConnectionIO[List[FUUID]] = for {
      txns <- select0
      _    <- update0(txns)
    } yield txns.map(_.txnId)

    result
  }

}
