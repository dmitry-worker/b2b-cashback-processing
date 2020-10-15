package com.noproject.common.data

trait Data[K, V] {

  def get(k: K): Option[V]

  def contains(k: K): Boolean

  def apply(k: K): V

  def isOrdered: Boolean

  def keys: Iterable[K]

  def values: Iterable[V]

  def toList: List[V]

  def size: Int

}
