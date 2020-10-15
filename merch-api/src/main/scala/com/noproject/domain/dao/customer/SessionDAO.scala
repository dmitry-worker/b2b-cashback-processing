package com.noproject.domain.dao.customer

import java.time.{Clock, Instant}

import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.domain.model.customer.Session
import doobie._
import doobie.implicits._
import javax.inject.{Inject, Singleton}

import scala.language.implicitConversions

@Singleton
class SessionDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override type T = Session

  override val tableName = "session"

  override val keyFieldList = List(
    "session_id"
  )

  override val allFieldList = List(
     "session_id",
     "customer_name",
     "session_token",
     "started_at",
     "expired_at",
  )

  def getByToken(token: String): IO[Option[Session]] = getByTokenTxn(token)

  def getByTokenTxn(token: String): ConnectionIO[Option[Session]] = {
    val q = s"select $allFields from $tableName where session_token = ? limit 1"
    Query[String, Session](q).toQuery0(token).option
  }

//  def getUnexpiredByToken(token: String, moment: Instant): IO[Option[Session]] = {
//    val q = s"select $allFields from $tableName where session_token = ? and expired_at > ? limit 1"
//    Query[(String, Instant), Session](q).toQuery0((token, moment)).option
//  }

  def insert(session: Session): IO[Int] = insertTxn(session)

  def insertTxn(session: Session): ConnectionIO[Int] = {
    val q = s"insert into $tableName ($valFields) values ($valHoles)"
    Update[(Option[String], String, Instant, Instant)](q)
      .toUpdate0(session.customerName, session.sessionToken, session.startedAt, session.expiredAt)
      .run
  }

  def updateExpirationByCustomer(custName: String, moment: Instant): IO[Int] = updateExpirationByCustomerTxn(custName, moment)

  def updateExpirationByCustomerTxn(custName: String, moment: Instant): ConnectionIO[Int] = {
    val sql = s"update $tableName set expired_at = ? where customer_name = ? and expired_at > ?"
    Update[(Instant, String, Instant)](sql).toUpdate0((moment, custName, moment)).run
  }

  def updateExpirationByToken(token: String, moment: Instant): IO[Int] = updateExpirationByTokenTxn(token, moment)

  def updateExpirationByTokenTxn(token: String, moment: Instant): ConnectionIO[Int] = {
    val sql = s"update $tableName set expired_at = ? where session_token = ?"
    Update[(Instant, String)](sql).toUpdate0((moment, token)).run
  }

}
