package com.noproject.common.stream

import cats.effect.IO
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext

class GroupingQueueResourceTest extends WordSpec with Matchers {

  implicit val cs = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)

  val (gq, shutdown) = GroupingQueueResource.getInstance[Int](2, 1).allocated.unsafeRunSync()

  "GroupingQueue" should {

    "receive when online" in {
      gq.put(1).unsafeRunSync()
      gq.put(2).unsafeRunSync()
      gq.output.take.unsafeRunSync() shouldEqual List(1, 2)
    }

    "complete when shutdown" in {
      gq.put(3).unsafeRunSync()
      shutdown.unsafeRunSync()
      assertThrows[IllegalStateException](gq.put(4).unsafeRunSync())
      gq.output.take.unsafeRunSync() shouldEqual List(3)
    }

  }


}
