package com.noproject.common.domain.dao.merchant

case class MerchantOfferMetrics(
  created:      Int
, updated:      Int
, unchanged:    Int
, deactivated:  Int
, active:       Int
, inactive:     Int
, total:        Int
) {

  require (total == active + inactive)
  require (total == created + updated + unchanged)

}