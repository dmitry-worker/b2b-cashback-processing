package com.noproject.service

import java.time.{Clock, Instant}

import cats.data.NonEmptyList
import cats.effect.{IO, Timer}
import cats.implicits._
import com.noproject.common.config.ConfigProvider
import com.noproject.common.controller.dto.TransactionSearchParams
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.CashbackTransactionDAO
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.transaction.{TxnChangeLogDAO, TxnDeliveryLogDAO, TxnDeliveryQueueDAO}
import com.noproject.common.domain.model.customer.Customer
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTransactionResponse, TxnChangeLogRow, TxnDeliveryLogRow, TxnLastDelivery}
import com.noproject.common.logging.DefaultLogging
import com.noproject.config.TxnDeliveryConfig
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import eu.timepit.fs2cron.awakeEveryCron
import fs2.Stream
import io.chrisdavenport.fuuid.FUUID
import io.circe.Json
import javax.inject.{Inject, Named, Singleton}
import org.http4s._
import org.http4s.circe.jsonEncoderOf
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import tsec.common._
import tsec.mac.jca.HMACSHA256

@Singleton
class TxnDeliveryService @Inject()(
  deliveryQueueDAO: TxnDeliveryQueueDAO
, deliveryLogDAO:   TxnDeliveryLogDAO
, changeLogDAO:     TxnChangeLogDAO
, transactionDAO:   CashbackTransactionDAO
, customerDAO:      CustomerDAO
, @Named("customerName")
  customer:         String
, transactor:       DefaultPersistence
, transport:        Client[IO]
, confProvider:     ConfigProvider[TxnDeliveryConfig]
, clock:            Clock
) extends DefaultLogging {


  // TODO: 10 txns per cycle is enough? configurable?
  private val CHUNK_SIZE = 10

  private implicit val encoder:EntityEncoder[IO, Json] = jsonEncoderOf[IO, Json]

  def runForever(implicit timer: Timer[IO]): IO[Unit] = {
    def action = customerDAO.findForDelivery(Some(customer)).flatMap { customers =>
      customers.map(c => deliver0(c)).sequence
    }

    for {
      conf   <- confProvider.getConfig
      stream =  awakeEveryCron[IO](conf.schedule) >> Stream.eval( action )
      _      <- stream.compile.drain
    } yield ()
  }

  // TODO: what if another qty of entries were updated?
  private[service] def handle0(batchId: FUUID, resp: Option[Status], txns: List[CashbackTransactionResponse], now: Instant): IO[Unit] = {
    def success: ConnectionIO[Unit] = {
      val logRecords = txns.map {
        txn => TxnDeliveryLogRow(txn.id, batchId, customer, now, txn.diff)
      }

      for {
        _ <- deliveryQueueDAO.removeFromQueue(batchId)
        _ <- deliveryLogDAO.insertTxn(logRecords)
      } yield ()
    }

    def retry(s: Option[Status]): ConnectionIO[Unit] = {
      for {
        _ <- deliveryQueueDAO.updateOutcome(batchId, s.map(_.code.toString))
      } yield ()
    }

    def decativate(s: Status): ConnectionIO[Unit] = {
      for {
        _ <- deliveryQueueDAO.updateOutcome(batchId, Some(s.code.toString))
        _ <- customerDAO.updateWebhookStatus(customer, false)
      } yield ()
    }

    val connectionIO = resp match {
      case Some(s) if s.responseClass == Status.Successful  => success
      case Some(s) if s == Status.Unauthorized              => retry(Some(s))
      case Some(s) if s.responseClass == Status.ServerError => retry(Some(s))
      case Some(s)                                          => decativate(s)
      case None                                             => retry(None)
    }
    connectionIO.transact(transactor.xar)
  }


  private[service] def composeDeliverySet(fuuid: FUUID, customer: String, now: Instant): IO[List[CashbackTransactionResponse]] = {
    import doobie.implicits._

    def getTransactionsFromQueue: ConnectionIO[List[CashbackTransaction]] = {
      deliveryQueueDAO.prepareAndUpdateRecords(fuuid, CHUNK_SIZE, customer, now).flatMap {
        case h :: tail =>
          val params = TransactionSearchParams().withIds(NonEmptyList(h, tail))
          transactionDAO.findTxn(params)
        case _ =>
          List[CashbackTransaction]().pure[ConnectionIO]
      }
    }

    def getDeliveriesInfo(txnIds: List[FUUID]): ConnectionIO[List[TxnLastDelivery]] = {
      if (txnIds.isEmpty)
        List[TxnLastDelivery]().pure[ConnectionIO]
      else {
        deliveryLogDAO.getLastDeliveryTimesByTxnIds(NonEmptyList.fromListUnsafe(txnIds))
      }
    }

    def composeChangeSet(txns: List[CashbackTransaction], lds: List[TxnLastDelivery], now: Instant): ConnectionIO[List[TxnChangeLogRow]] = {
      txns.map { txn =>
        val from = lds.find(_.txnId == txn.id).map(_.whenDelivered)
        changeLogDAO.find(txn.id, from, Some(now))
      }.sequence.map(_.flatten)
    }

    // apply composed changeset to origin txn and compare result with origin txn
    def isChangsetHasNoAffect(diff: Json, txn: CashbackTransaction): Boolean = {
      val txnJson = txn.asJson
      val restoredTxn = txnJson deepMerge diff
      txnJson equals restoredTxn
    }

    def diffOpt(diff: Json): Option[Json] = if (diff.isNull) None else Some(diff)

    def buildResponse(txns: List[CashbackTransaction], cs: List[TxnChangeLogRow]): List[CashbackTransactionResponse] = {
      txns.flatMap { txn =>
        val sortedCs = cs.filter(_.txnId == txn.id).sortBy(_.whenCreated).map(_.diff)
        val diff = sortedCs.reverse.fold(Json.Null)(_ deepMerge _)  // changeset jsons merged from last change to first
        if (isChangsetHasNoAffect(diff, txn)) None                  // changesets contains the opposite changes
        else Some(CashbackTransactionResponse(txn, diffOpt(diff)))
      }
    }

    val dbIO = for {
      txns       <- getTransactionsFromQueue
      deliveries <- getDeliveriesInfo(txns.map(_.id))
      changeSets <- composeChangeSet(txns, deliveries, now)
      res         = buildResponse(txns, changeSets)
    } yield {
      res
    }

    dbIO.transact(transactor.xar)
  }

  private[service] def deliver0(c: Customer):IO[Unit] = {
    val now = clock.instant

    def constructRequest(rawUrl: String, txnJson: Json, signature: String): IO[Request[IO]] = {
      val url = Uri
        .unsafeFromString(rawUrl.trim)
        .withQueryParam("signature", signature)
      Method.POST(txnJson, url)
    }

    def send(req: Request[IO]): IO[Option[Status]] = {
      transport.status(req).redeem({
        ex =>
          logger.error(ex)(s"Delivery service transport error")
          None
      }, s => Some(s))
    }

    for {
      fuuid <- FUUID.randomFUUID[IO]
      txns  <- composeDeliverySet(fuuid, c.name, now)
      json   = Json.fromValues(txns.map(_.asJson))
      key   <- HMACSHA256.buildKey[IO](c.webhookKey.get.utf8Bytes)
      sign  <- HMACSHA256.sign[IO](json.noSpaces.utf8Bytes, key)
      req   <- constructRequest(c.webhookUrl.get, json, sign.toHexString)
      _      = if (txns.nonEmpty) logger.info(s"${txns.size} new transactions for ${c.name} (webhook is ${c.webhookUrl.get})")
      resp  <- if (txns.isEmpty) IO.pure(Some(Status.NoContent)) else send(req)
      res   <- handle0(fuuid, resp, txns, now)
    } yield res
  }


}
