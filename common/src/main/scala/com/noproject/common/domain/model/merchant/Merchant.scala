package com.noproject.common.domain.model.merchant

case class Merchant(
  merchantName: String
, description:  String
, logoUrl:      String
, imageUrl:     Option[String]
, categories:   List[String]
, priceRange:   Option[String]
, website:      Option[String]
, phone:        Option[String]
, offers:       List[MerchantOffer]
)
