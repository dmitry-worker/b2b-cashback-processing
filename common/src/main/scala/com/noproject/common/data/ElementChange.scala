package com.noproject.common.data

import io.circe.Json

case class ElementChange[A](
  utype: ElementChangeType
, src: A
, diff: Option[Json] = None
)
