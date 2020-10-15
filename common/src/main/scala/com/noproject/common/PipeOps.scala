package com.noproject.common

object PipeOps {

  implicit class Pipe[A](val a: A) extends AnyVal {
    def <| [B](f: A => Unit): A = { f(a); a }
    def |> [B](f: A => B): B = f(a)
  }

}
