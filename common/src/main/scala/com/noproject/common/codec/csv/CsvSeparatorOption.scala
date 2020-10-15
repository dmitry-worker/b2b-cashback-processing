package com.noproject.common.codec.csv

import enumeratum.{Enum, EnumEntry}

sealed trait CsvSeparatorOption extends EnumEntry {
  def innerValue: String
  override def toString: String = innerValue
}

object CsvSeparatorOption extends Enum[CsvSeparatorOption] {
  override def values= findValues
  case object Comma     extends CsvSeparatorOption { override def innerValue: String = ","  }
  case object Semicolon extends CsvSeparatorOption { override def innerValue: String = ";"  }
  case object Tab       extends CsvSeparatorOption { override def innerValue: String = "\t" }
}