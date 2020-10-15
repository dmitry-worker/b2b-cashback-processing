package com.noproject.partner.mogl.model

import java.time.Instant

import io.circe.Json

case class MoglMerchantDetails(
  dateAdded:            Instant // 1519197523,
, startDate:            Instant // 1519197523,
, hasSchedule:          Boolean //   true,
, scheduleType:         MoglMerchantScheduleType//     "EXCLUDE",
, schedule:             Json // MoglMerchantSchedule -> unsupported now
, hasDefault:           Boolean //   false,
, renewalIntervalType:  Option[MoglMerchantRenewalIntervalType] // "DAY",
, cumulative:           Boolean //   false
)