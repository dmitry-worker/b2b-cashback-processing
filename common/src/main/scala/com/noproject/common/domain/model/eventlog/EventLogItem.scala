package com.noproject.common.domain.model.eventlog

import java.time.Instant

import io.circe.Json

case class EventLogItem(
  eventId:    Option[Long] = None
, timestamp:  Instant
, objectType: EventLogObjectType
, objectId:   Option[String]
, rawObject:  Option[Json]
, message:    String
, details:    Option[String]
)


