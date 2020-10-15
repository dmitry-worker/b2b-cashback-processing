package com.noproject.common.stream

import com.noproject.common.domain.model.eventlog.Loggable

trait LogProducer[F[_]] {
  def push[T <: Loggable](item: T, message: String, details: Option[String] = None): F[Unit]
}
