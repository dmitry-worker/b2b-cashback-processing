package com.noproject.partner.mogl.model

case class MoglUserInfo(
  id:                 Long
, firstname:          Option[String]
, lastname:           Option[String]
, thumbnailUrl:       String
, privacyLevel:       String
, address:            MoglAddress
, donatePercent:      Double
, numAlerts:          Int
){
  lazy val idStr = id.toString
}
