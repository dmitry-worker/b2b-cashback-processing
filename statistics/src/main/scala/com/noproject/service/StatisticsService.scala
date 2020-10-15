package com.noproject.service

import cats.implicits._
import cats.effect.IO
import com.noproject.common.domain.model.eventlog.EventLogItem
import com.noproject.common.domain.service.EventLogDataService
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.stream.RabbitConsuming


class StatisticsService(
  rcons:  RabbitConsuming[EventLogItem]
, elds:   EventLogDataService
) extends DefaultLogging {


  def runForever: IO[Unit] = {
    rcons.drainWithIO ( data => elds.insert(data.contents).void )
  }

}

