package com.noproject.common.domain.dao

import cats.implicits._
import doobie.util.Meta
import enumeratum._

import scala.reflect.runtime.universe.TypeTag
import scala.util.Try

object EnumSetConvertible {
    implicit def enumSetMeta[T <: EnumEntry](implicit t: Enum[T], tag: TypeTag[T]): Meta[Set[T]] = {
    Meta.Advanced.array[String]("text", "array").timap[Set[T]](
      (o: Array[String])   => o.map(item => Try (t.withName(item)).toEither.leftMap(e => sys.error(e.toString)).merge).toSet)(
      (a: Set[T]) => a.map(_.entryName).toArray)
  }
}
