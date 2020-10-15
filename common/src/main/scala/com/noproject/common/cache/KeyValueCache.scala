package com.noproject.common.cache

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicBoolean

import cats.implicits._
import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.effect.concurrent.{MVar, Ref}
import com.noproject.common.Executors
import com.noproject.common.cache.KeyValueCache.CMap

import scala.concurrent.duration._

class KeyValueCache[K, V](
  private[cache] val state:  Ref[IO, CMap[K,V]]
, private[cache] val shutdown:  Ref[IO, Boolean]
, ttlMillis: Int
, clock:  Clock
, graceMilllis: Option[Int] = None
)(implicit
  cs: ContextShift[IO]
, timer: Timer[IO]
) {

  private[cache] val sleepTime = {
    val milliseconds = graceMilllis match {
      case Some(ms) if ms > ttlMillis => ms
      case Some(ms)                   => ttlMillis + ms
      case _                          => ttlMillis + ttlMillis
    }
    milliseconds.millis
  }

  private def get(k: K): IO[Option[V]] = {
    shutdown.get.flatMap {
      case true => IO.raiseError(new RuntimeException("Cache is shut down"))
      case _    =>
        state.get.map { inner  =>
          val now = clock.instant()
          inner.get(k) match {
            case Some((time, value)) if time.isAfter(now) => Some(value)
            case _ => None
          }
        }
    }
  }

  private def update(k: K, v: V): IO[Unit] = {
    shutdown.get.flatMap {
      case true => IO.raiseError(new RuntimeException("Cache is shut down"))
      case _    =>
        val now = clock.instant()
        val butThen = now.plusMillis(ttlMillis)
        state.update { cmap =>
          val newValue = butThen -> v
          cmap + (k -> newValue)
        }
    }
  }

  private val refresh: IO[Unit] = IO.suspend {
      val now = clock.instant()
      shutdown.get.flatMap {
        case true => IO.unit
        case _    =>
          for {
            _   <- state.tryUpdate { cmap => cmap.filter { case (_, (time, _)) => time.isAfter(now) } }
            _   <- (timer.sleep(sleepTime) *> cs.shift *> refresh).start(cs)
          } yield ()
      }
  }

  def applyOrElse(k: K, f: => IO[V]): IO[V] = {
    shutdown.get.flatMap {
      case true => IO.raiseError(new RuntimeException("Cache is shut down"))
      case _    =>
        get(k).flatMap {
          case Some(v) => IO.pure(v)
          case _       => f.flatMap(v => update(k,v) *> IO.pure(v))
        }
    }
  }

}

object KeyValueCache {

  type CMap[K, V] = scala.collection.immutable.Map[K, (Instant, V)]

  def apply[K, V](ttlMillis: Int, clock: Clock, graceMillis: Option[Int] = None)(implicit cs: ContextShift[IO], timer: Timer[IO]): Resource[IO, KeyValueCache[K, V]] = {

    val allocate: IO[KeyValueCache[K, V]] = {
      for {
        ref      <- Ref.of[IO, CMap[K, V]](Map())
        shutdown <- Ref.of[IO, Boolean](false)
        cache     = new KeyValueCache(ref, shutdown, ttlMillis, clock, graceMillis)
        _        <- cache.refresh.start
      } yield cache
    }

    val release: KeyValueCache[K,V] => IO[Unit] = { kvc =>
      for {
        _ <- kvc.shutdown.set(true)
        _ <- kvc.state.set(Map())
      } yield ()
    }

    Resource.make(allocate)(release)

  }

}