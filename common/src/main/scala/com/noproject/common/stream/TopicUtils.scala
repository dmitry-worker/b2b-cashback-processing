package com.noproject.common.stream

import cats.{FlatMap, Monad}
import cats.effect.{Concurrent, IO, Resource}
import fs2.concurrent.{SignallingRef, Topic}

object TopicUtils {

  def buildTopic[A](implicit conc: Concurrent[IO]): Resource[IO, Topic[IO, StreamEvent[A]]] = {
    val acquire: IO[Topic[IO, StreamEvent[A]]] = for {
      topic <- Topic[IO, StreamEvent[A]](StreamStart)
      sref  <- SignallingRef[IO, Boolean](false)
    } yield topic
    val release: Topic[IO, StreamEvent[A]] => IO[Unit] = t => t.publish1(StreamStop)
    Resource.make(acquire)(release)
  }

}
