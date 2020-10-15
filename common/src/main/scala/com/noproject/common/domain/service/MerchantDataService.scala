package com.noproject.common.domain.service

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.Exceptions.OfferNotFoundException
import com.noproject.common.Executors
import com.noproject.common.config.{ConfigProvider, OffersConfig}
import com.noproject.common.controller.dto.OfferSearchParams
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.merchant.{MerchantDAO, MerchantOfferDAO, MerchantOfferDiffDAO, MerchantOfferMetrics}
import com.noproject.common.domain.model.merchant.{Merchant, MerchantOfferDiff, MerchantOfferRow, MerchantRow}
import com.noproject.common.logging.DefaultLogging
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import io.circe.generic.auto._
import com.noproject.common.codec.json.ElementaryCodecs._
import io.circe.syntax._
import java.time.{Clock, Instant}

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions

@Singleton
class MerchantDataService @Inject()(
  mDAO:    MerchantDAO
, moDAO:   MerchantOfferDAO
, modDAO:  MerchantOfferDiffDAO
, sp:      DefaultPersistence
, ocp:     ConfigProvider[OffersConfig]
, clock:   Clock
) extends DefaultLogging {

  implicit val ec: ExecutionContext = Executors.dbExec

  val duplicatePointsDistance = 150

  implicit def transaction[T](cio: ConnectionIO[T]): IO[T] = {
    cio.transact(sp.xar)
  }

  // *********
  // merchants
  // *********
  def insertMerchants(mercs: List[MerchantRow]): IO[Int] = mDAO.insert(mercs)

  def findMerchantsByNames(names: NonEmptyList[String]): IO[List[MerchantRow]] = mDAO.findByNames(names)

  // *********
  // offers
  // *********
  def insertOffers(offers: List[MerchantOfferRow]): IO[Int] = moDAO.insert(offers)

  def updateOffer(offer: MerchantOfferRow): IO[Int] = moDAO.update(offer)

  def findNotAvailableOfferOn(moment: Instant, network: String): IO[List[MerchantOfferRow]] = moDAO.findNotAvailableOn(moment, network)

  def markOffersInactive(offerIds: List[String], atTime: Instant): IO[Int] = moDAO.markInactive( atTime, offerIds )

  def markOffersUpdated(offerIds: List[String], atTime: Instant): IO[Int] = moDAO.markUpdated( offerIds, atTime )

  private def findWith(osparams: OfferSearchParams, customerName: Option[String], f: (OfferSearchParams, Option[String]) => IO[List[MerchantOfferRow]]): IO[List[Merchant]] = {
    for {
      conf   <- ocp.getConfig
      offers <- f(osparams, customerName)
      names   = offers.map(_.merchantName)
      mercs  <- names match {
        case h :: tail => findMerchantsByNames(NonEmptyList(h, tail))
        case _         => IO.pure(Nil)
      }
      groups <- IO.delay(offers.groupBy(_.merchantName))
    } yield {
      mercs.flatMap { m =>
        groups.get(m.merchantName).map(m.extern(_, conf.baseUrl, customerName.getOrElse("")))
      }
    }
  }

  def findOffers(osparams: OfferSearchParams): IO[List[MerchantOfferRow]] = moDAO.find(osparams)

  def findMerchantOffers(osparams: OfferSearchParams, customerName: Option[String]): IO[List[Merchant]] = {
    findWith(osparams, customerName, moDAO.find)
  }

  def findOfferById(id: String, customerName: Option[String]): IO[Merchant] = {
    findMerchantOffers(OfferSearchParams()
      .withIds(NonEmptyList(id, Nil)), customerName)
      .map(_.headOption).map {
      case Some(offer) => offer
      case None => throw OfferNotFoundException(id)
    }
  }

  def findNewOffers(limit: Int, customerName: Option[String]): IO[List[Merchant]] = {
    findWith(OfferSearchParams(limit = Some(limit)), customerName, moDAO.findNew)
  }

  def findFeaturedOffers(limit: Int, customerName: Option[String]): IO[List[Merchant]] = {
    findWith(OfferSearchParams(limit = Some(limit)), customerName, moDAO.findFeatured)
  }

  def getCategories(customerName: Option[String]): IO[List[String]] = {
    moDAO.getCategories(customerName)
  }

  // *********
  // metrics
  // *********
  def getMetrics(moment: Instant, network: String): IO[MerchantOfferMetrics] = moDAO.getMetrics(moment, network)


    // *********
  // diffs
  // *********
  def insertOfferDiff(diff: MerchantOfferDiff): IO[Int] = modDAO.insert(diff)

  def getOfferDiffs(offerId: String, timestamp: Instant): IO[List[MerchantOfferDiff]] = {
    val io = modDAO.find(offerId, timestamp)
    io.transact(sp.xar)
  }

  def restoreOffer(offerId: String, timestamp: Instant): IO[Option[MerchantOfferRow]] = {
    restoreOfferTxn(offerId, timestamp).transact(sp.xar)
  }

  def restoreOfferTxn(offerId: String, timestamp: Instant): ConnectionIO[Option[MerchantOfferRow]] = {
    val io = for {
      offer <- moDAO.findById(offerId)
      diffs <- modDAO.find(offerId, timestamp)
    } yield (offer, diffs)

    io.map {
      case (offer: Option[MerchantOfferRow], diffs: List[MerchantOfferDiff]) =>
        offer.map { o =>
          val ojson = o.asJson
          val changes = diffs.map(_.diff)
          val restoredJson = changes.fold(ojson)(_ deepMerge _)
          restoredJson.as[MerchantOfferRow].right.get
        }
    }
  }

  // *********
  // common
  // *********
  def deleteAllData(): IO[Unit] = {
    for {
      _ <- moDAO.deleteAll()
      _ <- mDAO.deleteAll()
      _ <- modDAO.deleteAll()
    } yield ()
  }
}
