package com.noproject.common.domain.service

import java.time.Instant

import cats.effect.IO
import com.noproject.common.domain.dao.EventLogDao
import com.noproject.common.domain.model.eventlog.{EventLogItem, Loggable}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._

@Singleton
class EventLogDataService @Inject()(dao: EventLogDao) {

  def insert(events: List[EventLogItem]): IO[Int] = {
    dao.insertMany(events)
  }

  def insert[T <: Loggable](o: T, message: String, details: Option[String]): IO[Int] = {
    dao.insert(o.asEventLogItem(message, details))
  }

  def deleteOld(tresholdDays: Long): IO[Int] = {
    val till = Instant.now.minusSeconds((tresholdDays days).toSeconds)
    dao.deleteTill(till)
  }
}
