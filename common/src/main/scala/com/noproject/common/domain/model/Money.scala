package com.noproject.common.domain.model

import com.noproject.common.Decimals

import scala.language.implicitConversions
import scala.math.BigDecimal.RoundingMode

object Money {

  val zero = Money(Decimals.zero)

  def apply(raw: BigDecimal): Money = new Money(raw.setScale(2, RoundingMode.HALF_UP))

}

/**
  * SIP-15 value class is much lighter than case class
  * https://docs.scala-lang.org/overviews/core/value-classes.html
  */
class Money private(val amount: BigDecimal) extends AnyVal {

  override def toString: String = String.valueOf(amount)

  def percent(percent: Percent): Money = {
    Money(this.amount * percent.amount / 100)
  }

  def +(that: Money): Money = {
    val amt = this.amount + that.amount
    Money(amt)
  }

  def -(that: Money): Money = {
    val amt = this.amount - that.amount
    Money(amt)
  }

  def *[A](that: A)(implicit numeric: Numeric[A]): Money = {
    val res = numeric.toDouble(that) * amount
    Money(res)
  }

  def /[A](that: A)(implicit numeric: Numeric[A]): Money = {
    val res = numeric.toDouble(that) / amount
    Money(res)
  }

}