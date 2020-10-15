package com.noproject.common.data

import org.scalatest.{Matchers, WordSpec}

class WeightedRandomTest extends WordSpec with Matchers {

  val weightedRandom = new WeightedRandom(Map("a" -> 0.5, "b" -> 0.25, "c" -> 0.125, "d" -> 0.0625))

  "WeightedRandomTest" should {

    "getValue" in {
      val results = (0 until 2000).map { _ => weightedRandom.getValue }
      val as = results.count(_ == "a")
      val bs = results.count(_ == "b")
      val cs = results.count(_ == "c")
      val ds = results.count(_ == "d")
      println(s"As: ${as} Bs: ${bs} Cs: ${cs} Ds: ${ds}")
      assert(ds < cs && cs < bs && bs < as)
    }

  }

}
