package com.noproject.common.data

import io.circe.Json
import org.scalatest.{Matchers, WordSpec}

class ElementDiffTest extends WordSpec with Matchers {

  case class Cat(name: String, age: Int, color: String)
  case class Dog(name: String, breed: String, ranking: Int)

  "ElementDiffTest" should {

    "apply" in {
      val c1 = Cat("Barsik", 10, "Grey")
      val c2 = Cat("Murka", 10, "Black")
      val c3 = Cat("Murka", 5, "Black")

      val diff = ElementDiff[Cat].calculate(c1, c2)
      val nm = diff.hcursor.downField("name")
      nm.as[String] shouldEqual Right(c1.name)

      val color = diff.hcursor.downField("color")
      color.as[String] shouldEqual Right(c1.color)

      val lastYear = Dog("Muhtar", "Ovcharka", 1)
      val thisYear = Dog("Muhtar", "Ovcharka", 2)

      val diff2 = ElementDiff[Dog].calculate(lastYear, thisYear)
      val rk = diff2.hcursor.downField("ranking")
      rk.as[Int] shouldEqual Right(lastYear.ranking)
    }

  }

}
