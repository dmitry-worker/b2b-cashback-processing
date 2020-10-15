package com.noproject.common.data

import enumeratum._
import enumeratum.EnumEntry.Lowercase

sealed trait ElementChangeType extends EnumEntry with Lowercase
object ElementChangeType extends Enum[ElementChangeType] {
  case object Create extends ElementChangeType
  case object Update extends ElementChangeType
  case object Delete extends ElementChangeType
  override def values = findValues
}
