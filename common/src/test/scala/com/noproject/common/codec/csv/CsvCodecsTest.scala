package com.noproject.common.codec.csv

import cats.data.NonEmptyList
import com.noproject.common.controller.dto.SearchParamsRule
import org.scalatest.{Matchers, WordSpec}
//import cats.implicits._

class CsvCodecsTest extends WordSpec with CsvCodecs with Matchers {

  case class Cat(name: String, age: Int, color: String)
  case class Dog(name: String, breed: Option[String], ranking: Int)

  val rn = "\r\n"

  "CsvCodecsTest" should {

    "apply" in {
      val c1 = Cat("Barsik", 10, "Grey")
      val fmt1 = CsvFormat(CsvSeparatorOption.Semicolon, CsvEscapeOption.Sep)
      val catCodec  = CsvCodecs.apply[Cat]
      val catWriter = catCodec.apply(fmt1)
      val catCsv    = catWriter.apply(c1)
      catWriter.header shouldEqual (s"name;age;color${rn}")
      catCsv shouldEqual s"""Barsik;10;Grey${rn}"""
      println(catCsv)

      val c2 = Dog("Muhtar", Some("Nemetskaya\tOvcharka"), 1)
      val rule = SearchParamsRule(true, NonEmptyList.fromListUnsafe("name" :: "ranking" :: Nil))
      val fmt2 = CsvFormat(CsvSeparatorOption.Tab, CsvEscapeOption.Sep, Some(rule))
      val dogCodec = CsvCodecs.apply[Dog]
      val dogWriter = dogCodec.apply(fmt2)
      val dogCsv = dogWriter.apply(c2)
      val tab = "\t"
      dogWriter.header shouldEqual (s"name\tranking${rn}")
      dogCsv shouldEqual s"""Muhtar	1${rn}"""
      println(dogCsv)
      succeed
    }

  }
}
