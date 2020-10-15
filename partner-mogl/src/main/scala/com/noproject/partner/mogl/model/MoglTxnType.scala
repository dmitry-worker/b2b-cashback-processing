package com.noproject.partner.mogl.model

import com.noproject.partner.mogl.model.MoglTxnType.findValues
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable

sealed trait MoglTxnType extends EnumEntry
object MoglTxnType extends Enum[MoglTxnType] {
  case object AUTHORIZED extends MoglTxnType
  case object CLEARED extends MoglTxnType
  case object REMOVED extends MoglTxnType
  case object REMOVED_DUP extends MoglTxnType
  case object FAILED extends MoglTxnType
  case object PAYMENT extends MoglTxnType

  override def values: immutable.IndexedSeq[MoglTxnType] = findValues
}