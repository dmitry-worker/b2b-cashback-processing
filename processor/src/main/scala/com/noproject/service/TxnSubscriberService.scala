package com.noproject.service

import java.time.{Clock, Instant}

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.noproject.common.Exceptions.LoggableException
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.service.CashbackTransactionDataService
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.stream.{RabbitConsuming, RabbitProducer, DefaultRabbitEnvelope}
import javax.inject.{Inject, Named, Singleton}

@Singleton
class TxnSubscriberService @Inject()(
  @Named("customerName")
  customer:     String
, txnDS:        CashbackTransactionDataService
, logProducer:  RabbitProducer[EventLogItem]
, cons:         RabbitConsuming[CashbackTransaction]
, clock:        Clock
)(implicit cs: ContextShift[IO], timer: Timer[IO]) extends DefaultLogging {

  def runForever: IO[Unit] = {
    val resIO = cons.drainWithIO { sre =>
      if (sre.size == 0) IO.unit
      else {
        val now   = clock.instant()
        val list  = NonEmptyList.fromListUnsafe(sre.contents)
        logger.debug(s"Processor received envelope of ${sre.size} txns with ids: ${list.map(_.id)}")

        // all references should be different!
        val sane = sre.contents.map(_.reference).toSet.size == sre.size

        if (sane) {
          for {
            errors <- txnDS.batchProcess(list, now)
            _      <- handleErrors(errors, now)
          } yield {
            logger.debug(s"Handled processing errors (${errors.size}) ${errors.mkString("\n", "\n", "\n")}")
          }
        } else {
          IO.delay {
            logger.error("Received insane package with duplicated references.")
          }
        }
      }
    }
    // TODO: don't recover, stop the service!
    resIO //.redeem(th => logger.error(th.getMessage), _ => ())
  }

  private[service] def handleErrors(exList: List[LoggableException[CashbackTransaction]], moment: Instant): IO[Unit] = {
    if (exList.nonEmpty) {
      val rk = exList.head.failureObject.eventLogType.entryName
      val message = exList.map(ex => s"${ex.failureObject.id} (${ex.message}: ${ex.details.getOrElse("")})").mkString(", ")
      logger.warn(s"Failed to process cashback txn(s): $message")

      val list = exList.map(ex => ex.failureObject.asEventLogItem(ex.message, ex.details))
      logProducer.submit(rk, list)
    } else IO.unit

  }

}
