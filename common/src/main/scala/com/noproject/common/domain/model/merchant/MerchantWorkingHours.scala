package com.noproject.common.domain.model.merchant

import java.time.LocalTime

case class MerchantWorkingHours(
  from:                LocalTime
, to:                  LocalTime
)
