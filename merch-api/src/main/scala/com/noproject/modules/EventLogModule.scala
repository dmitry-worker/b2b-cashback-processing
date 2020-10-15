package com.noproject.modules

import com.google.inject.AbstractModule
import com.noproject.common.domain.service.EventLogDataService
import com.noproject.service.schedule.EventLogScheduler
import net.codingwell.scalaguice.ScalaModule

class EventLogModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[EventLogDataService]
    bind[EventLogScheduler]
  }
}
