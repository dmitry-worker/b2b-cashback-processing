package com.noproject.modules

import com.google.inject.AbstractModule
import com.noproject.domain.service.statistics.StatsDataService
import com.noproject.service.statistics.{StatisticsScheduler, StatisticsService}
import net.codingwell.scalaguice.ScalaModule

class StatModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[StatsDataService]
    bind[StatisticsService]
    bind[StatisticsScheduler]
  }
}
