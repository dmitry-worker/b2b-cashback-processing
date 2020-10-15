package com.noproject.common.domain.dao.merchant

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.controller.dto.OfferSearchParams
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.{IDAO, JsonConvertible}
import com.noproject.common.domain.model.Location
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, _}
import doobie._
import doobie.implicits._
import io.circe.Json
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import shapeless.{::, HNil}

import scala.language.implicitConversions

@Singleton
class MerchantOfferDiffDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override type T = MerchantOfferDiff

  override val tableName = "merchant_offers_diff"

  override val keyFieldList = List(
    "offer_id",
    "timestamp"
  )


  override val allFieldList = List(
    "offer_id"
  , "timestamp"
  , "diff"
  )


  def insert(values: List[MerchantOfferDiff]): IO[Int] = super.insert0(values)

  def insert(value: MerchantOfferDiff): ConnectionIO[Int] = super.insert0(List(value))

  def findAll: IO[List[MerchantOfferDiff]] = super.findAll0

  def find(offerId: String, timestamp: Instant): ConnectionIO[List[MerchantOfferDiff]] = {
    val sql = s"select $allFields from $tableName where offer_id = ? and timestamp <= ? order by timestamp desc"
    Query[(String, Instant), MerchantOfferDiff](sql).toQuery0(offerId, timestamp).to[List]
  }
}
