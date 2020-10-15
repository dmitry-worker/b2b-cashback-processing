package com.noproject.common.domain.service

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.Executors
import com.noproject.common.cache.SimpleCache
import com.noproject.common.domain.dao.merchant.{MerchantCategoryMappingDAO, MerchantNameMappingDAO}
import com.noproject.common.domain.model.merchant.mapping.{MerchantMappings, MerchantNameMapping}
import javax.inject.{Inject, Singleton}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

@Singleton
class MerchantMappingDataServiceImpl @Inject()(
  mnDAO:    MerchantNameMappingDAO
, mcDAO:    MerchantCategoryMappingDAO
)(implicit t: Timer[IO], cs: ContextShift[IO]) extends MerchantMappingDataService {

  private lazy val cache = SimpleCache
    .apply[MerchantMappings](60, load)
    .unsafeRunTimed(1 seconds)
    .get

  def getMappings: IO[MerchantMappings] = cache.demand

  def getNameMappings: IO[Map[String, String]] = {
    getMappings.map(_.nameMappings)
  }

  def getCategoryMappings: IO[Map[String, Seq[String]]] = {
    getMappings.map(_.categoryMappings)
  }

  // names
  def addNameMapping(foreign: String, common: String): IO[Int] = {
    mnDAO.insert(MerchantNameMapping(foreign, common) :: Nil)
  }

  def deleteNameMappings(): IO[Int] = {
    mnDAO.deleteAll()
  }

  // categories
  def addCategoryMapping(foreign: String, common: String): IO[Int] = {
    // TODO: map categories appropriately
    IO.pure(0)
  }

  def deleteCategoryMappings(): IO[Int] = {
    // TODO: map categories appropriately
    IO.pure(0)
  }

  private def load: IO[MerchantMappings] = {
    for {
      names <- getNameMappings0
      cats  <- getCategoryMappings0
    } yield MerchantMappings(names, cats)
  }

  private def getNameMappings0: IO[Map[String, String]] = {
    mnDAO.findAll.map { mappings =>
      mappings
        .map { m => m.foreignName -> m.commonName }
        .toMap
    }
  }

  private def getCategoryMappings0: IO[Map[String, Seq[String]]] = {
    // TODO: map categories appropriately
    IO.pure(Map())
//    mcDAO.findAll[MerchantCategoryMapping]
//    db.run(categoryMappingDAO.findAll).map {
//      _.flatMap {
//        case MerchantCategoryMapping(their, our) =>
//          val t1 = their -> our
//          val t2 = our   -> our
//          t1 :: t2 :: Nil
//      }.foldLeft(mutable.Map.empty[String, List[String]]) {
//        case (map, (key, value)) =>
//          map.get(key) match {
//            case Some(values) =>
//              map.update(key, value :: values)
//            case None =>
//              map.update(key, value :: Nil)
//          }
//          map
//        }
//        .toMap
//    }
//    ???
  }


}
