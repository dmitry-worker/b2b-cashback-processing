package com.noproject.common.domain.service

import java.time.{Clock, Instant}

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.noproject.common.Exceptions.{LoggableException, TransactionNotFoundException}
import com.noproject.common.controller.dto.TransactionSearchParams
import com.noproject.common.data.ElementDiff
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.codec.DomainCodecs
import com.noproject.common.domain.dao.CashbackTransactionDAO
import com.noproject.common.domain.model.transaction.{CashbackTransaction, TxnChangeLogRow, TxnDeliveryQueueRow, TxnKey}
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import fs2.Stream
import io.circe.Json
import javax.inject.{Inject, Singleton}
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.partner.CustomerNetworksDAO
import com.noproject.common.domain.dao.transaction.{TxnChangeLogDAO, TxnDeliveryQueueDAO}
import com.noproject.common.logging.DefaultLogging

import scala.util.{Failure, Success, Try}
// required as fuuid codecs
import com.noproject.common.codec.json.ElementaryCodecs._

@Singleton
class CashbackTransactionDataService @Inject()(
  sp:     DefaultPersistence
, dqDAO:  TxnDeliveryQueueDAO
, logDAO: TxnChangeLogDAO
, cnDAO:  CustomerNetworksDAO
, cDAO:   CustomerDAO
, dao:    CashbackTransactionDAO
, clock:  Clock
) extends DefaultLogging {

  private val txnMutableFields = Set(
    "status"
  , "purchaseAmount"
  , "cashbackBaseUSD"
  , "cashbackTotalUSD"
  , "cashbackUserUSD"
  , "cashbackOwnUSD"
  , "whenSettled"
  , "whenReceived"
  , "whenPosted"
  , "rawTxn"
  , "whenCreated"
  , "whenUpdated"
  )

  def find(tsp: TransactionSearchParams, customerName: Option[String] = None): IO[Seq[CashbackTransaction]] = {
    for {
      rows <- dao.find(tsp, customerName)
    } yield rows
  }

  def findById(txnId: String, customerName: Option[String] = None): IO[CashbackTransaction] = {
    Try (TransactionSearchParams().withId(txnId)) match {
      case Failure(_)   => IO.delay(throw TransactionNotFoundException(txnId))
      case Success(tsp) => find(tsp, customerName).map(_.headOption).map {
        case Some(txn) => txn
        case None => throw TransactionNotFoundException(txnId)
      }
    }
  }

  def stream(tsp: TransactionSearchParams, customerName: Option[String] = None): Stream[IO, CashbackTransaction] = {
    dao.stream(tsp, customerName)
  }


  // processing stuff

  private val diffExcludedFields: Set[String] = Set(
    "rawTxn"
  , "payoutId"
  , "failedReason"
  , "rawTxn"
  , "offerId"
  , "offerTimestamp"
  , "whenCreated"
  , "whenUpdated"
  )

  type TxnPair = (CashbackTransaction, Option[CashbackTransaction])

  def batchProcess(newTransactions: NonEmptyList[CashbackTransaction], moment: Instant): IO[List[LoggableException[CashbackTransaction]]] = {

    def buildTxnPairs(consumed: List[CashbackTransaction], existed: Map[TxnKey, CashbackTransaction] ): List[TxnPair] = {
      newTransactions.toList.flatMap { txn =>
        val existedTxn = existed.get(txn.txnKey)
        if (existedTxn.isEmpty) {
          logger.debug(s"Creating txnId:${txn.id} ref:${txn.reference}")
          Some(txn, None)
        } else {
          val updatedTxn = txn.copy(id = existedTxn.get.id)
          if (updatedTxn === existedTxn.get)
            None
          else {
            logger.debug(s"Updating txnId:${existedTxn.get.id} ref:${txn.reference} | by:${txn.id}")
            Some(updatedTxn, existedTxn)
          }
        }
      }
    }

    // it could be possible if all consumed and existed txns are the same
    def processEmptyPairs: ConnectionIO[List[LoggableException[CashbackTransaction]]] = {
      logger.info(s"Batch txn process: nothing to update")
      List.empty[LoggableException[CashbackTransaction]].pure[ConnectionIO]
    }

    def processNonEmptyPairs(pairs: List[TxnPair], customer: String): ConnectionIO[List[LoggableException[CashbackTransaction]]] = {
      val (valid, invalid) = validate(pairs).partition(_.isRight)
      logger.debug(s"Validated ${valid.size} valid pairs and ${invalid.size} invalid pairs.")
      val txns = valid.map(_.right.get)
      val logs = pairs.collect { case (txn, Some(old)) =>
        val diff = DomainCodecs.txnDiff.calculate(old, txn, diffExcludedFields)
        TxnChangeLogRow(txn.id, customer, moment, diff)
      }
      val errors = invalid.map(_.left.get)
      for {
        affected  <- batchInsertOrUpdate(txns, moment)
        _          = logger.debug(s"Upserted ${affected} transactions")
        changelog <- logDAO.insertTxn(logs)
        queued    <- queueDeliveries(customer, txns, moment)
        _          = logger.info(s"Batch txn process: changed ${affected}, logged ${changelog}, queued ${queued}, invalid ${errors.size}")
      } yield errors
    }

    val customer = newTransactions.head.customerName
    require (newTransactions.forall(_.customerName === customer))

    val keys = newTransactions.map(_.txnKey)

    val io = dao.findAllByRefsAndNetworks(keys).flatMap { existing =>
      logger.debug(s"Found ${existing.size} existing transactions.")
      val pairs = buildTxnPairs(newTransactions.toList, existing)
      if (pairs.isEmpty) processEmptyPairs
      else processNonEmptyPairs(pairs, customer)
    }

    io.transact(sp.xar)
  }


  private def postErrors(errors: List[LoggableException[CashbackTransaction]]): ConnectionIO[Unit] =
    ().pure[ConnectionIO] // TODO

  private def batchInsertOrUpdate(txns: List[CashbackTransaction], moment: Instant): ConnectionIO[Int] = {
    logger.debug(s"Upserting ${txns.map(_.id)} valid transactions")
    dao.upsertTxn(txns, moment)
  }

  private def validate(tpl: List[TxnPair]): List[Either[LoggableException[CashbackTransaction], CashbackTransaction]] = {
    def validateForUpdate(txn: CashbackTransaction, oldTxn: CashbackTransaction): Either[LoggableException[CashbackTransaction], CashbackTransaction] = {

      def diffValid(oldTxn: CashbackTransaction, newTxn: CashbackTransaction): Either[LoggableException[CashbackTransaction], CashbackTransaction] = {
        val json: Json = DomainCodecs.txnDiff.calculate(oldTxn, newTxn, txnMutableFields)
        val diffSet = json.asObject.toList.flatMap(_.keys)
        Either.cond(diffSet.isEmpty
          , newTxn
          , LoggableException(newTxn,  s"Cannot update oldTxn: ${oldTxn.id} with newTxn: ${newTxn.id} - immutable field(s) violation: ${diffSet.mkString(", ")}"))
      }



      def statusValid(oldTxn: CashbackTransaction, newTxn: CashbackTransaction): Either[LoggableException[CashbackTransaction], CashbackTransaction] = {
        val result = Either.cond(oldTxn.possibleStatusChanges.contains(newTxn.status)
          , newTxn
          , LoggableException(newTxn, s"Cannot update status from ${oldTxn.status.entryName} to ${newTxn.status.entryName}"))

        result
      }

      def validate(oldTxn: CashbackTransaction, newTxn: CashbackTransaction): Either[LoggableException[CashbackTransaction], CashbackTransaction] = {
        newTxn.valid *> statusValid(oldTxn, newTxn) *> diffValid(oldTxn, newTxn)
      }

      validate(oldTxn, txn)
    }

    tpl.map { case (txn, oldTxn) =>
      // new txn
      if (oldTxn.isEmpty) txn.valid
      // old txn
      else validateForUpdate(txn, oldTxn.get)
    }
  }

  def queueDeliveries(customer: String, txns : List[CashbackTransaction], now: Instant): ConnectionIO[Int] = {
    for {
      activeNets    <- cnDAO.getNetworkNamesByCustomerNameTxn(customer).map(_.toSet)
      filtered       = txns.filter(txn => activeNets.contains(txn.merchantNetwork))
      rows           = filtered.map { ct => TxnDeliveryQueueRow.fromTxn( ct, now ) }
      isDeliverable <- cDAO.webhookActive( customer )
      queued        <- if( rows.nonEmpty && isDeliverable ) dqDAO.insertTxn( rows )
                       else 0.pure[ConnectionIO]
    } yield queued
  }

  def checkCountForDescription(suiteId: String): IO[Int] = {
    dao.checkCountForDescription(suiteId)
  }

}
