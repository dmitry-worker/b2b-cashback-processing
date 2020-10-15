package com.noproject.common.stream


import cats.effect.IO



trait RabbitProducer[T] {

  def submit(rk: String, messages: List[T]): IO[Unit]

}
