package com.noproject.service.schedule

import cats.effect.{IO, Timer}
import com.noproject.common.domain.service.EventLogDataService
import com.noproject.common.logging.DefaultLogging
import com.noproject.config.SchedulerConfig
import cron4s.Cron
import eu.timepit.fs2cron.awakeEveryCron
import fs2.Stream
import javax.inject.{Inject, Singleton}

@Singleton
class EventLogScheduler @Inject()(ds: EventLogDataService, conf: SchedulerConfig) extends DefaultLogging {

  private def deleteOldEvents = {
    for {
      _ <- ds.deleteOld(conf.eventLogTresholdDays)
    } yield logger.info("Old events deleted")
  }

  def scheduleRemoveOldEvents(implicit timer: Timer[IO]): Unit = {
    val cron      = Cron.unsafeParse(conf.statisticsCron)
    val job       = Stream.eval(deleteOldEvents)
    val scheduled = awakeEveryCron[IO](cron) >> job
    scheduled.compile.drain.unsafeRunAsync {
      case Left(ex) =>
        logger.error(ex.getMessage)
      case _ => // no action
    }
  }
}
