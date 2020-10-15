package com.noproject.common.controller.dto

import cats.data.NonEmptyList

case class SearchParamsRule[A](
  include:  Boolean
, values:   NonEmptyList[A]
) {

  lazy val valuesSet: Set[A] = values.foldLeft(Set[A]())(_+_)

  def allows(el: A): Boolean = {
    // if rule based on "includes" then values should contain el
    // else values should not contain this el.
    include == (valuesSet contains el)
  }


  def update(optThat: Option[SearchParamsRule[A]]): SearchParamsRule[A] = {
    optThat match {
      case None       => this
      case Some(that) =>
        if (this.include == that.include) {
          // TODO: .toNes creates SortedSet and requires an instance of Order[A]
          val thisSet = this.values.toList.toSet
          SearchParamsRule(this.include, this.values ++ that.values.filterNot(thisSet.contains))
        } else {
          SearchParamsRule(that.include, that.values)
        }
    }
  }

}
