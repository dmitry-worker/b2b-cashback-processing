package com.noproject.config

case class SchedulerConfig(
                          enabled: Boolean,
                          statisticsCron: String,
                          eventLogCron: String,
                          eventLogTresholdDays: Int
                          )
