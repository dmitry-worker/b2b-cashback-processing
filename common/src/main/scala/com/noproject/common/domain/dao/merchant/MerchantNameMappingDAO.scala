package com.noproject.common.domain.dao.merchant

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.Executors
import com.noproject.common.domain.model.merchant._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats.implicits._
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.merchant.mapping.MerchantNameMapping
import doobie.util.query.Query
import javax.inject.{Inject, Singleton}

@Singleton
class MerchantNameMappingDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override type T = MerchantNameMapping

  override val tableName = "merchant_name_mappings"

  override val keyFieldList = List(
    "foreign_name"
  )

  override val allFieldList = List(
    "foreign_name"
  , "common_name"
  )

  def insert(mappings: List[MerchantNameMapping]): IO[Int] = super.insert0(mappings)
  def findAll: IO[List[MerchantNameMapping]] = super.findAll0

}
