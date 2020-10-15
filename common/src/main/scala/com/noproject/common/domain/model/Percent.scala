package com.noproject.common.domain.model

import com.noproject.common.Decimals

import scala.language.implicitConversions
import scala.math.BigDecimal.RoundingMode


object Percent {

  val zero = Percent(Decimals.zero)

  def apply(i: Int): Percent  = apply(BigDecimal(i))
  def apply(l: Long): Percent = apply(BigDecimal(l))
  def apply(d: Double, mult: Boolean): Percent = {
    val value = if (mult) d * 100 else d
    apply(BigDecimal(value))
  }

  def apply(value: BigDecimal): Percent = {
    require (value >= 0)
    new Percent(value.setScale(2, RoundingMode.HALF_UP))
  }

  def mult[A](f: A, s: A)(implicit ev: Numeric[A]):A = {
    ev.times(f, s)
  }

}


/**
  * SIP-15 value class is much lighter than case class
  * https://docs.scala-lang.org/overviews/core/value-classes.html
  */
class Percent private(val amount: BigDecimal) extends AnyVal {

  override def toString: String = String.valueOf(amount) + "%"

  def percent(percent: Percent): Percent = {
    Percent(this.amount * percent.amount / 100)
  }

  def +(that: Percent): Percent = {
    val amt = this.amount + that.amount
    Percent(amt)
  }

  def -(that: Percent): Percent = {
    val amt = this.amount - that.amount
    Percent(amt)
  }

  def *[A](that: A)(implicit numeric: Numeric[A]): Percent = {
    val res = numeric.toDouble(that) * amount
    Percent(res)
  }

  def /[A](that: A)(implicit numeric: Numeric[A]): Percent = {
    val res = numeric.toDouble(that) / amount
    Percent(res)
  }

  def apply(that: Money): Money = {
    that.percent(this)
  }

}