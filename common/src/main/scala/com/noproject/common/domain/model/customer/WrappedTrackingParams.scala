package com.noproject.common.domain.model.customer

import java.time.Instant

import com.noproject.common.logging.DefaultLogging

object WrappedTrackingParams {

  val empty = WrappedTrackingParams(Consumer.empty, None, None)

}



case class WrappedTrackingParams(
  user:  Consumer
, offer: Option[String]
, time:  Option[Instant]
)