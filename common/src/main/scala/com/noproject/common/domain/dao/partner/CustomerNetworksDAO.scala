package com.noproject.common.domain.dao.partner

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.Executors
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.partner.CustomerNetwork
import doobie._
import doobie.implicits._
import doobie.util.query.Query
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

@Singleton
class CustomerNetworksDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  implicit val ec: ExecutionContext = Executors.dbExec

  override val tableName = "customer_networks"
  override val keyFieldList = Nil
  override val allFieldList = List("customer_name", "network_name")

  def getByCustomerName(customerName: String): IO[List[CustomerNetwork]] = {
    getByCustomerNameTxn(customerName)
  }

  def getByCustomerNameTxn(customerName: String): ConnectionIO[List[CustomerNetwork]] = {
    val q =
      s"""select
         |  n.*,
         |  cn.customer_name is not null as enabled
         |from network n left join customer_networks cn
         |  on n.network_name = cn.network_name
         |  and cn.customer_name = ?
       """.stripMargin
    Query[String, CustomerNetwork](q, None).toQuery0(customerName).to[List]
  }

  def getNetworkNamesByCustomerName(customerName: String): IO[List[String]] = {
    getNetworkNamesByCustomerNameTxn(customerName)
  }

  def getNetworkNamesByCustomerNameTxn(customerName: String): ConnectionIO[List[String]] = {
    val q = s"select network_name from $tableName where customer_name = ?"
    Query[String, String](q, None).toQuery0(customerName).to[List]
  }

  def insert(pairs: NonEmptyList[(String, String)]): IO[Int] = {
    insertTxn(pairs)
  }

  def insertTxn(pairs: NonEmptyList[(String, String)]): ConnectionIO[Int] = {
    val fr0 = s"INSERT INTO $tableName( customer_name, network_name ) VALUES( ?, ? )"
    Update[(String, String)]( fr0 ).updateMany( pairs )
  }

  def insert(customerName: String, networks: NonEmptyList[String]): ConnectionIO[Int] = {
    val rawSql = s"INSERT INTO $tableName( customer_name, network_name ) VALUES( ?, ? )"
    val tuples = networks.map( n => (customerName, n) )

    Update[(String, String)]( rawSql ).updateMany( tuples )
  }

  def delete(customerName: String, networks: NonEmptyList[String]): IO[Int] = deleteTxn(customerName, networks)

  def deleteTxn(customerName: String, networks: NonEmptyList[String]): ConnectionIO[Int] = {
    val delete    = Fragment.const(s"delete from $tableName")
    val fcustomer = fr"customer_name = $customerName"
    val fvalues   = Fragments.in(fr"network_name", networks)
    val result  = delete ++ Fragments.whereAnd(fcustomer, fvalues)
    result.update.run
  }

  def enableNetwork(cn: String, nw: String): IO[Int] = {
    insert(cn, NonEmptyList.one(nw) )
  }

  def disableNetwork(cn: String, nw: String): IO[Int] = {
    delete(cn, NonEmptyList.one(nw))
  }

  def networkEnabled( cn: String, nw: String ) : ConnectionIO[Boolean] = {
    val base = Fragment.const( s"SELECT COUNT(*) FROM $tableName" )
    val pred = fr"WHERE customer_name = $cn AND network_name = $nw"
    (base ++ pred).query[Int].unique.map( _ > 0 )
  }
}
