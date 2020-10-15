package com.noproject.common.data

import cats.kernel.Eq
import io.circe.{ACursor, Json}
import org.scalatest.{Matchers, WordSpec}
import io.circe.syntax._
import io.circe.generic.auto._

class DataChangeSetTest extends WordSpec with Matchers {

  case class Cat(name: String, age: Int)
  case class ComplexCat(id: Int, cat: Cat, anotherProperty: Int)
  val cats = Cat("Barsik", 1) :: Cat("Murka", 2) :: Cat("Leopold", 3) :: Nil
  val mods = Cat("Thomas", 5) :: cats.tail.map(c => c.copy(age = c.age + 1))
  implicit val eqCat = Eq.fromUniversalEquals[Cat]
  implicit val eqComplexCat = Eq.fromUniversalEquals[ComplexCat]

  val duo1 = DataUnordered[String, Cat](cats, c => c.name, Set())
  val duo2 = DataUnordered[String, Cat](mods, c => c.name, Set())

  "DataChangeSet" should {

    "be correctly computed" in {
      val diff = duo1 /|\ duo2
      println(s"Diff create:\n\n${diff.create.values.mkString("\n")}\n\n")
      println(s"Diff update:\n\n${diff.update.values.mkString("\n")}\n\n")
      println(s"Diff delete:\n\n${diff.delete.values.mkString("\n")}\n\n")
      diff.create.values.head.src shouldEqual Cat("Thomas", 5)
      diff.delete.values.head.src shouldEqual Cat("Barsik", 1)
    }

    "first object correctly restored with result and diff" in {
      val catOrigin = ComplexCat(1, Cat("Barsik", 1), 1)
      val catChange = ComplexCat(1, Cat("Pivasik", 2), 2)

      val origins = DataUnordered[Int, ComplexCat](List(catOrigin), c => c.id, Set())
      val changes = DataUnordered[Int, ComplexCat](List(catChange), c => c.id, Set())

      val diff = origins /|\ changes
      println(s"Diff create:\n\n${diff.create.values.mkString("\n")}\n\n")
      println(s"Diff update:\n\n${diff.update.values.mkString("\n")}\n\n")
      println(s"Diff delete:\n\n${diff.delete.values.mkString("\n")}\n\n")

      val catJson = diff.update.values.head.src.asJson
      val change = diff.update.values.head.diff.get
      val restoredCat = catJson deepMerge change
      restoredCat shouldEqual catOrigin.asJson
    }
  }
}
