package com.noproject.partner.button.domain.model

case class UsebuttonOrderItem(
  identifier:             String
, total:                  Option[UsebuttonCents]      = None
, amount:                 UsebuttonCents
, quantity:               Option[Int]                 = None
, publisher_commission:   Option[UsebuttonCents]      = None
, sku:                    Option[String]              = None
, upc:                    Option[String]              = None
, category:               Option[UsebuttonCategory]   = None
, description:            Option[String]              = None
, attributes:             Option[Map[String, String]] = None
)
