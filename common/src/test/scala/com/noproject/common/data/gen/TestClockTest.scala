package com.noproject.common.data.gen

import java.time.Instant

import org.scalatest.{Matchers, WordSpec}

class TestClockTest extends WordSpec with Matchers {

  var clock = TestClock.apply

  "TestClock" should {
    "Have a consistent value while unstepped" in {
      clock.instant() shouldEqual clock.instant()
    }

    "Not have a consistent value when stepped" in {
      val first : Instant = clock.instant()
      clock.step()
      val after : Instant = clock.instant()

      after should not be first
      after.compareTo( first ) should be > 0
    }
  }

}
