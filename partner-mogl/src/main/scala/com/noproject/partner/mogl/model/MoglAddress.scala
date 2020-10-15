package com.noproject.partner.mogl.model

case class MoglAddress(
  streetAddress:      Option[String]
, postalCode:         Option[String]
, city:               Option[String]
, state:              Option[String]
, metroId:            Option[String]
, metroName:          Option[String]
, metroImage:         Option[String]
, metroActive:        Boolean
)
