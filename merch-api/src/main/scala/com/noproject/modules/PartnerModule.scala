package com.noproject.modules

import com.google.inject.AbstractModule
import com.noproject.common.domain.service.NetworkDataService
import net.codingwell.scalaguice.ScalaModule

class PartnerModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[NetworkDataService]
  }
}
