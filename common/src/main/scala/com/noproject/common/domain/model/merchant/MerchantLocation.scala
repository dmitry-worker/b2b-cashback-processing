package com.noproject.common.domain.model.merchant

import com.noproject.common.domain.model.Location

case class MerchantLocation(
  point:          Location
, address:        Option[String]
, rating:         Option[Double]
, openingHours:   Map[String, Seq[MerchantWorkingHours]]
, acceptedCards:  List[String]
, imageUrls:      List[String]
, phone:          Option[String]
)
