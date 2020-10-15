package com.noproject.partner.button.domain.model

import com.noproject.common.domain.model.Money


case class UsebuttonCents(value: Long) {

  def toMoney: Money = {
    val src = BigDecimal(value)
    val hundredPercent = BigDecimal(100)
    Money(src / hundredPercent)
  }

}