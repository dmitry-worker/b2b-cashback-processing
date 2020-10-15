package com.noproject.common.data

import io.circe.{Encoder, Json}
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, LabelledGeneric, Witness}

trait ElementDiff[T] {
  def calculate(f: T, s: T, dontTrack: Set[String] = Set()): Json
}

object ElementDiff {

  implicit def apply[A](implicit ed: ElementDiff[A]): ElementDiff[A] = ed

  implicit class JsonPlus(_this: Json) {
    def |+|(_that: Json): Json = {
      if (!_this.isObject) _that
      else if (!_that.isObject) _this
      else _this deepMerge _that
    }
  }

  implicit def ed[A, R <: HList](implicit genA: LabelledGeneric.Aux[A, R], jd: ElementDiff[R]): ElementDiff[A] = {
    (f: A, s: A, dontTrack: Set[String]) =>
      jd.calculate(genA.to(f), genA.to(s), dontTrack)
  }

  implicit def ed0: ElementDiff[HNil] = {
    (_: HNil, _: HNil, _: Set[String]) => Json.Null
  }

  implicit def ed1[H, K <: Symbol, T <: HList](
    implicit
      tailDiff: ElementDiff[T]
    , enc: Encoder[H]
    , keys: Witness.Aux[K]
  ): ElementDiff[FieldType[K, H] :: T] = (f: H :: T, s: H :: T, doNotTrack: Set[String]) => {
    val name = keys.value.name
    val tail = tailDiff.calculate(f.tail, s.tail, doNotTrack)
    val same = f.head == s.head || doNotTrack.contains(name)
    val current =
      if (same) Json.Null
      else Json.fromFields(List(name -> enc(f.head)))
    current |+| tail
  }

}
