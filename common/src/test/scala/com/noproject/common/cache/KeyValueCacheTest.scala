package com.noproject.common.cache

import java.time.Clock

import cats.effect._
import cats.implicits._
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

class KeyValueCacheTest extends WordSpec with Matchers {

  private val clock = Clock.systemUTC

  private implicit val cs    = IO.contextShift(ExecutionContext.global)
  private implicit val timer = IO.timer(ExecutionContext.global)

  private val (cache, shutdown): (KeyValueCache[String, Int], IO[Unit]) = KeyValueCache.apply(100, clock, None).allocated.unsafeRunSync()

  "KeyValueCacheTest" should {

    "get" in {
      cache.applyOrElse("one", IO.delay("one".hashCode)).unsafeRunSync()
      cache.state.get.unsafeRunSync()("one")._2 shouldEqual "one".hashCode
    }

    "refresh" in {
      Thread.sleep(300)
      cache.state.get.unsafeRunSync().get("one") shouldEqual None
    }

    "shutdown" in {
      cache.shutdown.set(true).unsafeRunSync()
      assertThrows[RuntimeException](cache.applyOrElse("one", IO.delay("one".hashCode)).unsafeRunSync())
    }

  }

}
