package com.noproject.common.domain.model.merchant

import java.time.Instant

import com.noproject.common.domain.model.Location
import io.circe.Json

case class MerchantOffer(
  offerId:                String
, offerDescription:       String
, offerLocation:          Option[Location]
, offerAddress:           Option[String]
, tocText:                Option[String]
, tocUrl:                 Option[String]
, images:                 List[String]
, whenActivated:          Instant
, whenDeactivated:        Option[Instant]
, requirements:           MerchantRequirements
, reward:                 MerchantReward
, acceptedCards:          List[String]
, network:                String
, isOnline:               Boolean
, trackingUrl:            Option[String]
)