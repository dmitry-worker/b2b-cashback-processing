package com.noproject.common.domain.model.customer

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait AccessRole extends EnumEntry with Snakecase
object AccessRole extends Enum[AccessRole] {
  case object Noaccess extends AccessRole // no access to private api
  case object Customer extends AccessRole // access to customer's api
  case object Admin    extends AccessRole // access to admin's api
  override def values = findValues
}
