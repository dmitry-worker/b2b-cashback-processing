package com.noproject.common.stream

import cats.effect.{ContextShift, Fiber, IO, Resource, Timer}
import cats.effect.concurrent.{MVar, Ref}

import scala.concurrent.duration._

class GroupingQueueResource[T](
  private val input:    MVar[IO, Option[T]]
,         val output:   MVar[IO, List[T]]
, private val fiber:    Fiber[IO, Unit]
, private val shutdown: Ref[IO, Boolean]
) {

  def put(el: T): IO[Unit] = {
    for {
      shut <- shutdown.get
      _    <- shut match {
        case true => IO.raiseError(new IllegalStateException("Queue shut down"))
        case _    => input.put(Some(el))
      }
    } yield ()
  }

}

object GroupingQueueResource {

  def getInstance[T](groupBy: Int, withinSeconds: Int)(implicit c: ContextShift[IO], t: Timer[IO]): Resource[IO, GroupingQueueResource[T]] = {

    val acquire: IO[GroupingQueueResource[T]] = {
      for {
        input     <- MVar.empty[IO, Option[T]]
        output    <- MVar.empty[IO, List[T]]
        shutdown  <- Ref.of[IO, Boolean](false)
        fiber     <- fs2.Stream.unfoldEval(input)(q => q.take.map(t2 => t2.map(_ -> q)))
                      .groupWithin(groupBy, withinSeconds seconds)
                      .evalMap(g => output.put(g.toList))
                      .compile
                      .drain
                      .start
      } yield new GroupingQueueResource(input, output, fiber, shutdown)
    }

    val release: GroupingQueueResource[T] => IO[Unit] = { gq =>
      for {
        _ <- gq.shutdown.set(true)
        _ <- gq.input.put(None)
        _ <- gq.fiber.join
      } yield ()
    }


    Resource.make(acquire)(release)
  }

}