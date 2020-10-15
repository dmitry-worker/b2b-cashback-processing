package com.noproject.common.data

import com.noproject.common.data.gen.RandomValueGenerator

import scala.collection.immutable.TreeMap

class WeightedRandom[A](weights: Map[A, Double]) {

  private lazy val (tree, total) = weights
    .foldLeft(TreeMap[Double, A]() -> 0D) {
      case ((tmap, sum), (value, prob)) =>
        val newMap = tmap + (sum -> value)
        val newPrb = sum + prob
        newMap -> newPrb
    }

  def getValue: A = {
    weights.size match {
      case 1 =>
        weights.head._1
      case _ =>
        val key = RandomValueGenerator.randomDouble * total
        tree.to(key).last._2
    }
  }

}
