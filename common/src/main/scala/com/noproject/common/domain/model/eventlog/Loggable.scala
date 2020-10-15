package com.noproject.common.domain.model.eventlog

abstract class Loggable {
  def eventLogType: EventLogObjectType
  def asEventLogItem(message: String, details: Option[String] = None): EventLogItem
}



