package com.noproject.common.stream

import cats.effect.{IO, Timer}
import cats.implicits._
import com.noproject.common.Exceptions.LoggableException
import com.noproject.common.logging.DefaultLogging
import fs2.Stream

import scala.concurrent.duration._

abstract class LoopStream(implicit timer: Timer[IO]) extends DefaultLogging {

  protected def errorStream(err: Throwable): Stream[IO, Unit]

  def init(program: IO[Unit], retry: FiniteDuration = 5.seconds): IO[Unit] =
    run(Stream.eval(program), retry)

  private def run(program: Stream[IO, Unit], retry: FiniteDuration): IO[Unit] =
    loop(program, retry, 1).compile.drain

  private def loop(program: Stream[IO, Unit], retry: FiniteDuration, count: Int): Stream[IO, Unit] = {
    program.handleErrorWith { err =>
      logger.error(s"$err")
      errorStream(err) *> loop(Stream.sleep(retry) >> program, retry, count + 1)
    }
  }
}

class SimpleLoopStream(implicit timer: Timer[IO]) extends LoopStream {
  override protected def errorStream(err: Throwable): Stream[IO, Unit] = Stream.empty
}

class HandledLoopStream(implicit errorProducer: LogProducer[IO], timer: Timer[IO]) extends LoopStream {
  override def errorStream(err: Throwable): Stream[IO, Unit] = {
    err match {
      case ex: LoggableException[_] => Stream.eval(errorProducer.push(ex.failureObject, ex.message, ex.details))
      case _                        => Stream.eval(IO.unit)
    }
  }
}

object SimpleLoopStream {
  def apply(program: IO[Unit], retry: FiniteDuration = 5.seconds)
           (implicit timer: Timer[IO]): IO[Unit] = {
    (new SimpleLoopStream).init(program, retry)
  }
}

object HandledLoopStream {
  def apply(program: IO[Unit], retry: FiniteDuration = 5.seconds)
           (implicit errorProducer: LogProducer[IO], timer: Timer[IO]): IO[Unit] = {
    (new HandledLoopStream).init(program, retry)
  }
}
