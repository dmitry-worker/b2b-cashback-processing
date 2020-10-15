package com.noproject.common.domain.dao

import java.time.Instant

import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.model.eventlog.{EventLogItem, EventLogObjectType}
import doobie._
import doobie.implicits._
import cats.implicits._
import io.circe.Json
import javax.inject.{Inject, Singleton}
import scala.language.implicitConversions
import shapeless.{::, HNil}

@Singleton
class EventLogDao @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override type T = EventLogItem

  override def tableName: String = "event_log"

  override def keyFieldList: List[String] = List("event_id")

  override def allFieldList: List[String] = List(
    "event_id"
  , "timestamp"
  , "object_type"
  , "object_id"
  , "raw_object"
  , "message"
  , "details"
  )
  
  type VALUES_TYPE = Instant :: EventLogObjectType :: Option[String] :: Option[Json] :: String :: Option[String] :: HNil

  def findAll: IO[List[EventLogItem]] = findAllTxn

  def findAllTxn: ConnectionIO[List[EventLogItem]] = super.findAll0

  def findByType(eventType: EventLogObjectType): IO[List[EventLogItem]] = {
    val fr0 = Fragment.const(s"select ${allFields} from ${tableName}")
    val fr1 = Fragments.whereAnd(fr"object_type = $eventType")
    (fr0 ++ fr1).query[EventLogItem].to[List]
  }

  def insert(e: EventLogItem): IO[Int] = insertTxn(e)

  def insertMany(events: List[EventLogItem]): IO[Int] = insertManyTxn(events)

  def insertManyTxn(events: List[EventLogItem]): ConnectionIO[Int] = {
    val sql = s"insert into $tableName ($valFields) values ($valHoles)"
    val values = events.map { e =>
      e.timestamp :: e.objectType :: e.objectId :: e.rawObject :: e.message :: e.details :: HNil
    }
    Update[VALUES_TYPE](sql).updateMany(values)

  }


  def insertTxn(e: EventLogItem): ConnectionIO[Int] = {
    val sql = s"insert into $tableName ($valFields) values ($valHoles)"
    Update[(Instant, EventLogObjectType, Option[String], Option[Json], String, Option[String])](sql)
      .toUpdate0((e.timestamp, e.objectType, e.objectId, e.rawObject, e.message, e.details))
      .run
  }

  def deleteTill(till: Instant): IO[Int] = {
    val sql = s"delete from $tableName where timestamp < ?"
    Update[Instant](sql).toUpdate0(till).run
  }

}
