package com.noproject.common

import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext.global

object TestIO {
  implicit val t: Timer[IO]         = IO.timer(global)
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
}