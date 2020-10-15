package com.noproject

import cats.effect.{IO, Timer}
import com.google.inject.Injector
import com.noproject.config.SchedulerConfig
import com.noproject.service.schedule.EventLogScheduler
import com.noproject.service.statistics.StatisticsScheduler

object Scheduler {
  def run(injector: Injector)(implicit timer: Timer[IO]): Unit = {
    lazy val conf = injector.getInstance(classOf[SchedulerConfig])
    lazy val statsSched = injector.getInstance(classOf[StatisticsScheduler])
    lazy val eventSched = injector.getInstance(classOf[EventLogScheduler])

    if (conf.enabled) {
      statsSched.scheduleStatsUpdate
      eventSched.scheduleRemoveOldEvents
      // add other scheduled tasks here
    }
  }
}
