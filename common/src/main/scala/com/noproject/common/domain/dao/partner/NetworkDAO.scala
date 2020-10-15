package com.noproject.common.domain.dao.partner

import cats.effect.IO
import com.noproject.common.Executors
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.partner.Network
import doobie._
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

@Singleton
class NetworkDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  implicit val ec: ExecutionContext = Executors.dbExec

  override type T = Network

  override val tableName = "network"

  override val keyFieldList = List[String]()

  override val allFieldList = List("network_name", "description")

  def insert(values: List[Network]): IO[Int] = insertTxn(values)

  def insertTxn(values: List[Network]): ConnectionIO[Int] = super.insert1(values)

  def findAll: IO[List[Network]] = findAllTxn

  def findAllTxn: ConnectionIO[List[Network]] = super.findAll0

  def deleteByName(name: String): IO[Int] = {
    val q = s"delete from $tableName where network_name = ?"
    Update[String](q, None).toUpdate0(name).run
  }
}
