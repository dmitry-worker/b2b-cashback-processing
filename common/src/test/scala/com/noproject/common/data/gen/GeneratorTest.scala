package com.noproject.common.data.gen

import org.scalatest.{Matchers, WordSpec}
import scala.reflect.runtime.universe._
import shapeless.Generic

class GeneratorTest extends WordSpec with Matchers {

  case class Cat(name: String, age: Int, color: String)
  case class Cage(material: String, cat: Cat)

  case class HNilExample()

  import HlistGenerator._

  "GeneratorTest" should {

    "apply" in {
      val cats = (0 until 10).map { _ => Generator[Cat].apply }
      // all names are different
      cats.map(_.name).toSet.size shouldEqual 10
    }

    "apply nested" in {
      implicit val cgen = Generator[Cat]
      val cages = (0 until 10).map { _ => Generator[Cage].apply }
      // cats names inside cages are different
      cages.map(_.cat.name).toSet.size shouldEqual 10
    }

    "customize" in {
      implicit val sGen: Generator[String] = new Generator[String] {
        override def apply: String = "Barsik"
      }
      val cats = (0 until 10).map { _ => Generator[Cat].apply }
      assert( cats.forall(_.name == "Barsik") )
    }

  }

}
