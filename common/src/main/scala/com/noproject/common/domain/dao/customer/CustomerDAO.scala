package com.noproject.common.domain.dao.customer

import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.{EnumSetConvertible, IDAO}
import com.noproject.common.domain.model.customer.{AccessRole, Customer}
import doobie._
import doobie.implicits._
import javax.inject.{Inject, Singleton}

import scala.language.implicitConversions

@Singleton
class CustomerDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override type T = Customer

  override val tableName = "customer"

  override val keyFieldList = List(
    "name"
  )

  override val allFieldList = List(
    "name"
  , "api_key"
  , "hash"
  , "role"
  , "webhook_url"
  , "webhook_key"
  , "active"
  , "webhook_active"
  )

  implicit val accessRoleMeta = EnumSetConvertible.enumSetMeta[AccessRole]

  val selectAllFr = Fragment.const( s"SELECT $allFields FROM $tableName")

  def findAll: IO[List[Customer]] = super.findAll0

  def findForDelivery(customerName: Option[String] = None):IO[List[Customer]] = {
    val fr0 = Fragment.const(s"select $allFields from $tableName")
    val fr1 = Some(fr"webhook_url is not null")
    val fr2 = Some(fr"webhook_key is not null")
    val fr3 = Some(fr"webhook_active = true")
    val fr4 = Some(fr"active = true")
    val fr5 = customerName.map(s => fr"name = $s")
    val where = Fragments.whereAndOpt(fr1, fr2, fr3, fr4, fr5)
    (fr0 ++ where).query[Customer].to[List]
  }

  def updateWebhookStatus(customerName: String, isWebhookActive: Boolean): ConnectionIO[Int] = {
    val sql = s"update $tableName set webhook_active = ? where name = ?"
    Update[(Boolean, String)](sql).toUpdate0(isWebhookActive, customerName).run
  }

  def webhookActive( customerName: String ) : ConnectionIO[Boolean] = {
    val select = Fragment.const( s"SELECT webhook_active FROM $tableName")
    val pred = Fragments.whereAnd( fr"name = $customerName" )
    (select ++ pred).query[Boolean].option.map(_.exists(identity))
  }

  def getByName(customerName: String): IO[Option[Customer]] = {
    (selectAllFr ++ fr"WHERE active = TRUE AND name = $customerName")
      .query[Customer].option
  }

  def getByKey(key: String): IO[Option[Customer]] = getByKeyTxn( key )

  def getByKeyTxn( key: String ) : ConnectionIO[ Option[ Customer ] ] = {
    (selectAllFr ++ fr"WHERE active = TRUE AND api_key = $key")
      .query[Customer].option
  }

  def insertTxn(customers: List[Customer]): ConnectionIO[Int] = super.insert0(customers)

  def insert(customers: List[Customer]): IO[Int] = insertTxn(customers)

  def insertIfNotExistsTxn(customers: List[Customer]): ConnectionIO[Int] = super.insert1(customers)

  def insertIfNotExists(customers: List[Customer]): ConnectionIO[Int] = insertIfNotExistsTxn(customers)

//  def insert(customer: Customer): IO[Int] = super.insert0(List(customer))

  def delete(key: String): IO[Int] = deleteTxn(key)

  def deleteTxn(key: String): ConnectionIO[Int] = {
    val deleteFr = Fragment.const( s"UPDATE $tableName SET active = FALSE" )
    (deleteFr ++ fr"WHERE active = TRUE AND api_key = $key")
      .update.run
  }

  def undelete( key: String ): IO[Int] = undeleteTxn(key)

  def undeleteTxn( key: String ): ConnectionIO[Int] = {
    val undelete = Fragment.const( s"UPDATE $tableName SET active = TRUE" )
    (undelete ++ fr"WHERE active = FALSE AND api_key = $key")
      .update.run
  }

  /**
    * Update the set of roles available to a customer by api_key
    * @param key API key used to find the customer
    * @param roles New set of roles available to the user (replaces the old)
    * @return 0 if the referred-to customer could not be found, 1 if the update was successful.
    */
  def updateRoles( key: String, roles : Set[AccessRole] ) : ConnectionIO[Int] = {
    sql"""UPDATE customer
          SET role = $roles
          WHERE active = TRUE
            AND api_key = $key"""
      .update.run
  }

  def updateWebhook(key: String, webhookUrl: String, webhookKey: Option[String]): IO[Int] = {
    sql"""UPDATE customer
          SET    webhook_url    = $webhookUrl,
                 webhook_key    = $webhookKey,
                 webhook_active = true
          WHERE  api_key        = $key"""
      .update.run
  }

}
