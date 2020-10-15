package com.noproject.common.codec.csv

import enumeratum.{Enum, EnumEntry}

sealed trait CsvEscapeOption extends EnumEntry

object CsvEscapeOption extends Enum[CsvEscapeOption] {
  override def values= findValues
  case object All extends CsvEscapeOption
  case object Sep extends CsvEscapeOption
}