package com.noproject.common.service

import java.time.{Clock, Instant}

import cats.data.NonEmptyList
import cats.effect.{Concurrent, IO, Timer}
import cats.implicits._
import com.noproject.common.controller.dto.OfferSearchParams
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO, MerchantOfferMetrics}
import com.noproject.common.data.{DataChangeSetContents, DataUnordered, ElementChange, ElementChangeType}
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.dao.UnknownTransactionDAO
import com.noproject.common.domain.model.customer.{Consumer, TransactionPrototype, WrappedTrackingParams}
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, MerchantRow}
import com.noproject.common.domain.model.merchant.{MerchantOfferDiff, MerchantOfferRow, MerchantRow}
import com.noproject.common.domain.model.transaction
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import com.noproject.common.domain.service.MerchantDataService
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.stream.RabbitProducer
import io.circe.generic.auto._
import com.noproject.common.codec.json.ElementaryCodecs._
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.service.PartnerTrackingService
import io.circe.{Encoder, Json}
import io.prometheus.client.{CollectorRegistry, Gauge}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

abstract class CommonIntegrationService(
  networkName: String
, unknownTxnDAO: UnknownTransactionDAO
, txnProducer: RabbitProducer[CashbackTransaction]
, mds: MerchantDataService
, ts:  PartnerTrackingService
, cr: CollectorRegistry
, clock: Clock
, levelDB: LevelDBPersistence[CashbackTransaction]
)(implicit timer: Timer[IO], conc: Concurrent[IO]) extends DefaultLogging {

  private implicit val encoder: Encoder[ElementChangeType] = Encoder.instance[ElementChangeType] {
    ect => Json.fromString(ect.entryName)
  }

  def leveldb2rabbit: IO[Unit] = {
    val now = clock.instant()
    val txnStream = levelDB.getTxnStream.groupWithin(50, 1 seconds)
      .evalMap { txns =>
        val list = txns.toList
        sendTxnsToRabbit(list, now).flatMap( _ =>
          // if all is ok - remove from db
          levelDB.removeTxnsByRef(list.map(_.reference))
        )
      }
    val streamDrainIO = txnStream.compile.drain
    streamDrainIO.recover { case th: Throwable =>
      // we should stop this stream, set off an alarm and delay future execution
      logger.error(th)("Failed putting txns into rabbit")
    }
  }

  protected[service] def submitTransactions(txns: List[CashbackTransaction]): IO[Unit] = {
    levelDB.insertRecords(txns.map(txn => txn.reference -> txn))
  }

  protected[service] def transformPrototypes(atTime: Instant, mms: MerchantMappings, src: List[TransactionPrototype]): IO[List[CashbackTransaction]] = {

    def resolve(hashes: Map[String, Try[WrappedTrackingParams]]): List[CashbackTransaction] = {
      src.map { txn =>
        val params = txn.getEncodedParams match {
          case None =>
            logger.warn(s"Empty tracking params for txn ${txn.txnId}")
            WrappedTrackingParams.empty
          case Some(uid) =>
            hashes(uid) match {
              case Success(wtp) =>
                wtp
              case Failure(th) =>
                th.printStackTrace()
                logger.warn(s"Couldn't recover tracking params for txn ${txn.txnId}")
                WrappedTrackingParams.empty
            }
        }
        txn.asCashbackTransaction(params, atTime, mms)
      }
    }

    for {
      hashes <- ts.decodeHashesBatchAndGetTrackingParams(src.flatMap(_.getEncodedParams))
      result <- IO.delay( resolve(hashes) )
    } yield result

  }

  private[service] def sendTxnsToRabbit(txns: List[CashbackTransaction], now: Instant): IO[Unit] = {

    def sendGroups(groups: Map[String, List[CashbackTransaction]]): IO[List[Unit]] = {
      val ios = groups.map { case (cust, custTxns) => txnProducer.submit(s"$cust", custTxns) }
      ios.toList.sequence
    }

    def writeFailedTxns(failedTxns: List[CashbackTransaction], atTime: Instant): IO[Int] = {
      require (failedTxns.forall(_.customerName == Consumer.Unknown))
      if   (failedTxns.isEmpty) IO.pure(0)
      else unknownTxnDAO.upsert(failedTxns, atTime)
    }

    // this will make our txns list distinct on reference
    implicit val order = scala.math.Ordering.by[CashbackTxnStatus, Int]( st => CashbackTxnStatus.indexOf(st) )
    val filtered = txns.sortBy(_.status).foldLeft(Map[String, CashbackTransaction]()) {
      (res, txn) => res + (txn.reference -> txn)
    }

    val groups = filtered.values.toList.groupBy(_.customerName)

    for {
      _  <- writeFailedTxns(groups.getOrElse(Consumer.Unknown, List()), now)
      _  <- sendGroups(groups - Consumer.Unknown)
    } yield ()

  }

  protected[service] def updateMerchantRows(merchants: List[MerchantRow]): IO[Unit] = {
    val newDU = DataUnordered[String, MerchantRow](merchants, _.merchantName, Set())
    for {
      pers  <- mds.findMerchantsByNames(NonEmptyList.fromListUnsafe(newDU.keys.toList))
      persDU = DataUnordered[String, MerchantRow](pers, _.merchantName, Set())
      chSet  = persDU /|\ newDU
      _     <- mds.insertMerchants(chSet.create.toList.map(_.src))
    } yield ()
  }

  protected[service] def updateOfferRows( offers: List[MerchantOfferRow], atTime: Instant ): IO[Unit] = {
    for {
      pers  <- mds.findOffers(OfferSearchParams().withIds(NonEmptyList.fromListUnsafe(offers.map(_.offerId))))
      persDU = DataUnordered[String, MerchantOfferRow](pers, _.offerId, Set("whenActivated", "whenUpdated", "whenModified", "offerRawSrc"))
      newDU  = DataUnordered[String, MerchantOfferRow](offers, _.offerId, Set("whenActivated", "whenUpdated", "whenModified", "offerRawSrc"))
      chSet  = persDU /|\ newDU
      _     <- createOffers0(chSet.create)
      _     <- updateOffers0(atTime, chSet.update)
    } yield ()
  }

  protected[service] def createOffers0(du: DataChangeSetContents[String, MerchantOfferRow]): IO[Unit] = {
    val offers = du.toList
    for {
      _ <- mds.insertOffers(offers.map(_.src))
//      _ <- offerProducer.submit(networkName, offers)
    } yield ()
  }

  protected def updateOffers0(atTime: Instant,du: DataChangeSetContents[String, MerchantOfferRow]): IO[Unit] = {
    def updateOfferAndStoreDiff(eu: ElementChange[MerchantOfferRow]) = {
      for {
        _ <- mds.updateOffer(eu.src)
        _ <- if (eu.diff.isDefined) mds.insertOfferDiff(MerchantOfferDiff(eu.src.offerId, atTime, eu.diff.get))
             else IO.unit
      } yield ()
    }

    val offers = du.toList
    for {
      _ <- offers.map { eu => updateOfferAndStoreDiff(eu) }.sequence
//      _ <- offerProducer.submit(networkName, offers)
    } yield ()
  }

  protected def deleteOffers0( atTime: Instant ): IO[Unit] = {
    for {
      del  <- mds.findNotAvailableOfferOn(atTime, networkName)
      chg   = del.map { d => ElementChange(ElementChangeType.Delete, d) }
//      _    <- offerProducer.submit(networkName, chg)
      _    <- mds.markOffersInactive( del.map( _.offerId ), atTime )
    } yield ()
  }

  protected def updateMetrics0( atTime: Instant ): IO[Unit] = {
    logger.info(s"Got metrics for ${atTime}. Updating them now.")
    mds.getMetrics(atTime, networkName).map { summary =>
      logger.info(
        s"""
           |Metrics values:
           | created ${summary.created},
           | updated ${summary.updated},
           | unchanged ${summary.unchanged},
           | deactivated ${summary.deactivated},
           | active ${summary.active},
           | inactive ${summary.inactive}
         """.stripMargin)
      metrics.update(summary)
    }
  }

  lazy val metrics = MetricsCollection(
    summary = Gauge.build().name(networkName + "_offers").help(s"Qty of offers via ${networkName}").labelNames("metric").register(cr)
  )

}


case class MetricsCollection(summary: Gauge) {
  def update(metrics: MerchantOfferMetrics): Unit = {
    summary.labels("created").set(metrics.created)
    summary.labels("updated").set(metrics.updated)
    summary.labels("unchanged").set(metrics.unchanged)
    summary.labels("deactivated").set(metrics.deactivated)
    summary.labels("active").set(metrics.active)
    summary.labels("inactive").set(metrics.inactive)
    summary.labels("total").set(metrics.total)
  }
}