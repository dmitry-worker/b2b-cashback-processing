package com.noproject.stress.config


case class StressTestConfig(
  azigo:    Option[StressTestPartnerConfig]
, button:   Option[StressTestPartnerConfig]
, coupilia: Option[StressTestPartnerConfig]
)
