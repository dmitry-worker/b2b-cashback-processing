package com.noproject.common.config

import enumeratum.{Enum, EnumEntry}

sealed trait EnvironmentMode extends EnumEntry
object EnvironmentMode extends Enum[EnvironmentMode] {
  override def values= findValues
  case object Prod     extends EnvironmentMode
  case object Dev      extends EnvironmentMode
  case object Stage    extends EnvironmentMode
  case object Test     extends EnvironmentMode
}
