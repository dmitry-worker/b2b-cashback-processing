package com.noproject.common.cache

import java.time.Instant

import cats.effect.concurrent.MVar
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * In contrary to `memoize` it will never re-issue `load` on empty cache
  *
  * @param state - MVar (see cats.effect.concurrent)
  *              an asynchronous blocking queue implementation
  *              take - waits (nonblocking) while empty
  *              put  - waits (nonblocking) while full
  * @tparam A - inner value type
  */
abstract class SimpleCache[A](
  val state: MVar[IO, (Instant, Try[A])]
, ttlSeconds: Int
)(implicit timer: Timer[IO], cs: ContextShift[IO]) {

  def get: IO[Try[A]] = {
    for {
      now     <- timer.clock.realTime(MILLISECONDS).map(Instant.ofEpochMilli)
      curr    <- state.take
      result  <- curr match {
        case avail @ (i, value) if i.isAfter(now) =>
          state.tryPut(avail).map(_ => value)
        case _ =>
          val till = now.plusSeconds(ttlSeconds)
          load.timeout(2 seconds).map( Success.apply )
              .flatMap { s => state.tryPut( (till -> s) ).map(_ => s) }
              .recoverWith { case th: Throwable => state.put(till, Failure(th)) *> IO.raiseError(th) }
      }
    } yield result
  }

  def demand: IO[A] = get.flatMap {
    case Success(value) => IO.pure(value)
    case Failure(th)    => IO.raiseError(th)
  }

  def load: IO[A]

}

object SimpleCache {

  def apply[A](ttlSeconds:Int, f: => IO[A])(implicit timer: Timer[IO], cs: ContextShift[IO]): IO[SimpleCache[A]] = {
    for {
      now  <- timer.clock.realTime(MILLISECONDS).map(Instant.ofEpochMilli)
      init  = now -> Failure(new IllegalStateException("Cache not initialized."))
      mvar <- MVar.of[IO, (Instant, Try[A])](init)
    } yield {
      new SimpleCache[A](mvar, ttlSeconds) {
        override def load: IO[A] = f
      }
    }
  }

}