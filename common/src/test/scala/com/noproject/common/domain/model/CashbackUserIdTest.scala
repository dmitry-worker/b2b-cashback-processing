package com.noproject.common.domain.model

import org.scalatest.WordSpec

class CashbackUserIdTest extends WordSpec {

  val valid = "1::23" :: "vkjhdf::324" :: "3249::fjsdhfdsjh" :: Nil
  val invalid = "::" :: "::fhij38" :: "flkdsjf2i::" :: "fdskl::kfdjf::fkdjf" :: Nil

  "CashbackUserIdTest" should {

    "unapply" in {

      val src = "1::two"

      src match {
        case CashbackUserId(CashbackUserId("1", "two")) => succeed
        case _ => fail
      }

      val validated = valid.map {
        case CashbackUserId(cui) => Some(cui)
        case _                   => None
      }
      assert(validated.forall(_.isDefined))

      val invalidated = invalid.map {
        case CashbackUserId(cui) => Some(cui)
        case _                   => None
      }

      assert(invalidated.forall(_.isEmpty))
    }
  }
}
