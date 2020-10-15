package com.noproject.stress.data.gen

import fs2.Stream
import cats.effect.IO

trait StreamingGenerator[A] {

  def genStream: Stream[IO, A]

}
