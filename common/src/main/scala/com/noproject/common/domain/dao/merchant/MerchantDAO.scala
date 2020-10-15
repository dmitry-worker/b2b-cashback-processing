package com.noproject.common.domain.dao.merchant

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.domain.model.merchant.MerchantRow
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats.implicits._
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import doobie.util.query.Query
import javax.inject.{Inject, Singleton}
import shapeless.{::, HNil}

@Singleton
class MerchantDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override type T = MerchantRow

  override val tableName = "merchants"

  override val keyFieldList = List(
    "merchant_name"
  )

  override val allFieldList = List(
    "merchant_name"
  , "description"
  , "logo_url"
  , "image_url"
  , "categories"
  , "price_range"
  , "website"
  , "phone"
  )

  override def ginField = "search_index_gin"

  def exist(names: NonEmptyList[String]): IO[List[String]] = {
    val fr1 = Fragment.const(s"select merchant_name from $tableName where ")
    val fr2 = Fragments.in(fr"merchant_name", names)
    (fr1 ++ fr2).query[String].to[List]
  }

  def notExist(names: NonEmptyList[String]): IO[List[String]] = {
    exist(names).map {
      case Nil  => names.toList
      case list => list.foldLeft(names.toList.toSet)(_ - _).toList
    }
  }

  def getMerchantByName(name: String): IO[Option[MerchantRow]] = getMerchantByNameTxn(name)

  def getMerchantByNameTxn(name: String): ConnectionIO[Option[MerchantRow]] = {
    val q = s"select $allFields from $tableName where merchant_name = ?"
    Query[String, MerchantRow](q, None).toQuery0(name).option
  }

  def findByNames(names: NonEmptyList[String]): IO[List[MerchantRow]] = {
    val fr1 = Fragment.const(s"select $allFields from $tableName where ")
    val fr2 = Fragments.in(fr"merchant_name", names)
    (fr1 ++ fr2).query[MerchantRow].to[List]
  }

  def insert(values: List[MerchantRow]): IO[Int] = insertTxn(values)

  def insertTxn(values: List[MerchantRow]): ConnectionIO[Int] = {
    val valuesWithGin = values.map(v => v :: v.searchIndex :: HNil )
    super.insertGin1(valuesWithGin)
  }

  def findAll: IO[List[MerchantRow]] = findAllTxn

  def findAllTxn: ConnectionIO[List[MerchantRow]] = super.findAll0


  def delete(names: NonEmptyList[String]): IO[Int] = {
    val q = Fragment.const(s"delete from $tableName where ")
    val res = q ++ Fragments.in(fr"merchant_name", names)
    res.update.run
  }

}
