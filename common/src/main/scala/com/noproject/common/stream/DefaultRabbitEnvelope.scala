package com.noproject.common.stream

import java.time.Instant

/**
  * This will be a wrapper for batch rabbit delivery
  * @param size - size of a batch
  * @param date - date it was created
  * @param routing  - routing key (empty for non-routing submits
  * @param contents - the data itself
  * @tparam T - the `contents` elements type
  */
case class DefaultRabbitEnvelope[T](
  size:     Int
, date:     Instant
, routing:  Option[String]
, contents: List[T]
)
