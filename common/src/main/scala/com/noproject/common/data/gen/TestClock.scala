package com.noproject.common.data.gen

import java.time.temporal.{ChronoUnit, TemporalUnit}
import java.time.{Clock, Instant, ZoneId}

/**
  * Clock useful for testing purposes. Like the fixed clock, but allows stepping.
  */
class TestClock( startingAt: Instant
               , stepBy: Long
               , stepUnit: TemporalUnit
               , zoneId : ZoneId ) extends Clock {

  private var next = startingAt

  override def getZone: ZoneId = zoneId
  override def withZone( newZoneId: ZoneId ): Clock = new TestClock( startingAt, stepBy, stepUnit, newZoneId )
  override def instant(): Instant = next

  def step(): Unit = next = next.plus( stepBy, stepUnit )
}

object TestClock {
  def apply : TestClock = new TestClock( Instant.now, 100, ChronoUnit.MILLIS, ZoneId.systemDefault )
}

