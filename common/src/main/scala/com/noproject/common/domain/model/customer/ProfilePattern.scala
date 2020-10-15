package com.noproject.common.domain.model.customer

import com.noproject.common.data.WeightedRandom

case class ProfilePattern(
  male: Boolean
, ageRange: Range
, incomeRange: Range
, behavior: Map[String, Double]
) {

  val weightedMap = new WeightedRandom[String](behavior)

  def matches(profile: ConsumerProfile): Boolean = {
    (
       profile.male.contains(this.male)
    && profile.age.exists(a => this.ageRange.contains(a))
    && profile.incomeClass.exists(i => this.incomeRange.contains(i))
    )
  }

}