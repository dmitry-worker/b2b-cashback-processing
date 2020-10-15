package com.noproject.common.stream

sealed trait StreamEvent[+A]

case class StreamData[+A](data: A) extends StreamEvent[A]

case object StreamStart            extends StreamEvent[Nothing]

case object StreamStop             extends StreamEvent[Nothing]