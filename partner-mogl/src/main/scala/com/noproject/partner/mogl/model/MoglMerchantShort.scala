package com.noproject.partner.mogl.model

case class MoglMerchantShort(
  id:                 Long
, name:               String
, latitude:           Double
, longitude:          Double
, phone:              String
, thumbnailUrl:       String
, rating:             Double
, ratingCount:        Int
, yelpId:             String
, address:            MoglAddress
, applicationId:      Long
)