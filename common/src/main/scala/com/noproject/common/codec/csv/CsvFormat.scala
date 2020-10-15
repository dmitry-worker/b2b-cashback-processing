package com.noproject.common.codec.csv

import com.noproject.common.controller.dto.SearchParamsRule
import enumeratum.{Enum, EnumEntry}


case class CsvFormat(
  separator:  CsvSeparatorOption
, escape:     CsvEscapeOption
, rule:       Option[SearchParamsRule[String]] = None
) {

  def allows(el: String): Boolean = rule.forall(_ allows el)

}








