package com.noproject.common.domain.model.eventlog

import java.time.Instant

import io.circe.Json

object JsonEventLogItem {
  implicit class JsonEventLogItem(_this: Json) extends Loggable {
    override def eventLogType: EventLogObjectType = EventLogObjectType.UsebuttonTxn
    override def asEventLogItem(message: String, details: Option[String] = None): EventLogItem = {
      EventLogItem(None, Instant.now, eventLogType, None, Some(_this), message, details)
    }
  }
}


