package com.noproject.domain.dao.customer

import java.time.Instant

import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.{EnumSetConvertible, IDAO}
import com.noproject.common.domain.model.customer.AccessRole
import com.noproject.domain.model.customer.{CustomerSession, Session}
import doobie._
import doobie.implicits._
import javax.inject.{Inject, Singleton}

import scala.language.implicitConversions

// FIXME: table joins, complex queries are managed with DataService instances.
@Singleton
class CustomerSessionDAO @Inject()( protected val sp: DefaultPersistence
                                  , sessionDAO: SessionDAO
                                  , customerDAO: CustomerDAO
                                  ) extends IDAO {
  override type T = CustomerSession

  override def tableName = "session s JOIN customer c ON s.customer_name = c.name"
  val queryTableName = "session s JOIN customer c ON s.customer_name = c.name"

  override def allFieldList =
    List( "s.session_id"
        , "s.customer_name"
        , "s.session_token"
        , "s.started_at"
        , "s.expired_at"
        , "c.role"
        )

  override def keyFieldList = List( "s.session_id")

  implicit val accessRoleMeta: Meta[Set[AccessRole]] = EnumSetConvertible.enumSetMeta[AccessRole]
  implicit val customerSessionRead: Read[CustomerSession] =
    Read[(Long, Option[String], String, Instant, Instant, Set[AccessRole])]
      .map( t => CustomerSession( t._1, t._2, t._3, t._4, t._5, t._6 ) )

  private val selectAllFr = Fragment.const( s"SELECT $allFields FROM $tableName" )

  // Pass these through to the session DAO. We do not maintain the table.
  override def deleteAll(): IO[Int] = sessionDAO.deleteAll()
  override def deleteAllTxn(): ConnectionIO[Int] = sessionDAO.deleteAllTxn()

  def insert( session: Session ) : IO[CustomerSession] = { insertTxn(session) }

  def insertTxn( session: Session ) : ConnectionIO[CustomerSession] = {
    for {
      holes <-
        sql"""WITH
              rows AS (
                SELECT name, role
                FROM customer
                WHERE name = ${session.customerName}
                LIMIT 1 ),
              ins AS (
                INSERT INTO session ( customer_name, session_token, started_at, expired_at )
                SELECT name, ${session.sessionToken}, ${session.startedAt}, ${session.expiredAt}
                FROM rows
                RETURNING session_id, customer_name
              )
              SELECT ins.session_id, rows.role
              FROM ins, rows"""
        .query[(Long, Set[ AccessRole ])]
        .unique
    } yield { CustomerSession( session.copy( sessionId = holes._1), holes._2 ) }
  }

  def getByToken( token: String ) : IO[ Option[CustomerSession] ] = getByTokenTxn(token)

  def getByTokenTxn( token: String ) : ConnectionIO[ Option[CustomerSession] ] = {
    (selectAllFr ++ fr"WHERE s.session_token = $token LIMIT 1")
      .query[CustomerSession]
      .option
  }

}
