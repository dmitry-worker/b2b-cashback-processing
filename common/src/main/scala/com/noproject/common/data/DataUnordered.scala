package com.noproject.common.data

import cats.Eq
import cats.implicits._
import shapeless.{HList, LabelledGeneric}

class DataUnordered[K, V: Eq](val contentsMap: Map[K,V], val id: V => K, val dontTrack: Set[String]) extends Data[K, V] {

  def -(vs: Iterable[K]): DataUnordered[K, V] = {
    val newMap = vs.foldLeft(contentsMap)(_ - _)
    new DataUnordered(newMap, this.id, dontTrack)
  }

  def --(vs: Iterable[V]): DataUnordered[K, V] = {
    this - vs.map(id)
  }

  def --(that: Data[K,V]): DataUnordered[K, V] = {
    this - that.keys
  }

  def partition(predV: V => Boolean): (DataUnordered[K, V], DataUnordered[K, V]) = {
    val (f,s) = contentsMap.partition { case (k,v) => predV(v) }
    new DataUnordered(f, id, dontTrack) -> new DataUnordered(s, id, dontTrack)
  }

  def ++(vs: Iterable[V]): DataUnordered[K, V] = {
    val newMap = contentsMap ++ vs.map(v => id(v) -> v).toMap
    new DataUnordered(newMap, this.id, dontTrack)
  }

  def \(seq: List[K]): DataOrdered[K, V] = order(seq)

  def \\(seq: List[V]): DataOrdered[K, V] = order(seq.map(id))

  def order(seq: List[K]): DataOrdered[K, V] = {
    val ordered = seq.flatMap { key => contentsMap.get(key).map(v => key -> v) }
    val (keys, values) = ordered.unzip
    new DataOrdered(keys, DataUnordered.apply(values, id, dontTrack))
  }

  def /|\[R <: HList](that: DataUnordered[K,V])(implicit lg: LabelledGeneric.Aux[V, R], jd: ElementDiff[R]): DataChangeSet[K, V] = {
    val create = (that -- this).values.map { c => ElementChange(ElementChangeType.Create, c)}
    val deletedValues = this -- that
    val delete = deletedValues.values.map { c => ElementChange(ElementChangeType.Delete, c)}
    val update = (this -- deletedValues).values.flatMap { _this =>
      val _that = that(id(_this))
      val elDiff = ElementDiff[V].calculate(_this, _that, dontTrack)
      if (elDiff.isNull) None else {
        Some(ElementChange(ElementChangeType.Update, _that, Some(elDiff)))
      }
    }
    // TODO: maybe diff should be included as well
    implicit val updateEq: Eq[ElementChange[V]] = Eq.by[ElementChange[V], V](_.src)
    val createdata = DataUnordered[K, ElementChange[V]](create, el => id(el.src), Set())
    val deletedata = DataUnordered[K, ElementChange[V]](delete, el => id(el.src), Set())
    val updatedata = DataUnordered[K, ElementChange[V]](update, el => id(el.src), Set())
    DataChangeSet(createdata, updatedata, deletedata)
  }

  def apply(k: K): V = {
    contentsMap(k)
  }

  def contains(key: K): Boolean = {
    contentsMap.contains(key)
  }

  def containsNot(key: K): Boolean = {
    !contentsMap.contains(key)
  }

  override def get(k: K): Option[V] = contentsMap get k

  override def isOrdered: Boolean = false

  override def keys: Iterable[K] = contentsMap keys

  override def values: Iterable[V] = contentsMap values

  override def toList: List[V] = contentsMap.values toList

  override def size: Int = contentsMap.size

}


object DataUnordered {

//  def apply[K, V](src: Iterable[V], id: V => K)(implicit eq: Eq[V]): DataUnordered[K, V] = {
//    new DataUnordered[K,V](Map(), id) ++ src
//  }

  def apply[K, V](src: Iterable[V], id: V => K, dontTrack: Set[String])(implicit eq: Eq[V]): DataUnordered[K, V] = {
    new DataUnordered[K,V](Map(), id, dontTrack) ++ src
  }

}