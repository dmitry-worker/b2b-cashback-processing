package com.noproject.common.data

import cats.Eq

class DataOrdered[K, V](order: List[K], val unorderedData: DataUnordered[K,V]) extends Data[K, V] {

  def -(vs: Iterable[K]): DataOrdered[K, V] = {
    val newUO = unorderedData - vs
    val newOrder = order.filter( newUO.contains )
    new DataOrdered(newOrder, newUO)
  }

  def --(vs: Iterable[V]): DataOrdered[K, V] = {
    this.-(vs.map(unorderedData.id))
  }

  def ++(vs: List[V]): DataOrdered[K, V] = {
    val addKeys = vs.map(unorderedData.id).filter(unorderedData.containsNot)
    val newData = unorderedData ++ vs
    val newOrder = order ++ addKeys
    new DataOrdered(newOrder, newData)
  }

  def toList: List[V] = order.map(unorderedData.apply)

  override def get(k: K): Option[V] = unorderedData.get(k)

  override def contains(k: K): Boolean = unorderedData.contains(k)

  override def apply(k: K): V = unorderedData.apply(k)

  override def isOrdered: Boolean = true

  override def keys: Iterable[K] = unorderedData.keys

  override def values: Iterable[V] = unorderedData.values

  override def size: Int = unorderedData.size

}

object DataOrdered {

  def apply[K,V](src: List[V], id: V => K, dontTrack: Set[String])(implicit eq: Eq[V]): DataOrdered[K,V] = {
    val uo = DataUnordered(src, id, dontTrack)
    new DataOrdered(src.map(id), uo)
  }

}


