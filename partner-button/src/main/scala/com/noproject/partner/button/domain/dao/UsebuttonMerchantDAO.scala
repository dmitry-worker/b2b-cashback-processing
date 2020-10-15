package com.noproject.partner.button.domain.dao

import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.partner.button.domain.model.{UsebuttonMerchantRow}
import javax.inject.{Inject, Singleton}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

@Singleton
class UsebuttonMerchantDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {
  override type T = UsebuttonMerchantRow

  override def tableName: String = "merchant_usebutton"

  override def keyFieldList: List[String] = List("id")

  override def allFieldList: List[String] = List(
    "id",
    "name",
    "categories",
    "available_platforms",
    "supported_products",
    "status",
    "cpi_fixed",
    "cpa_percent",
    "cpa_fixed",
    "featured",
    "deactivation_date",
    "revenue_share_percent",
    "terms_and_conditions",
    "exclusive_offer",
    "homepage_url",
    "toc_url",
    "icon_url",
    "logo_url",
    "description"
  )

  def findAll: IO[List[UsebuttonMerchantRow]] = findAllTxn

  def findAllTxn: ConnectionIO[List[UsebuttonMerchantRow]] = super.findAll0

  def insert(ms: List[UsebuttonMerchantRow]): IO[Int] = insertTxn(ms)

  def insertTxn(ms: List[UsebuttonMerchantRow]): ConnectionIO[Int] = super.insert0(ms)

}
