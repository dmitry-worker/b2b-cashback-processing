package com.noproject.modules

import com.google.inject.AbstractModule
import com.noproject.common.domain.service.CustomerDataService
import com.noproject.domain.service.customer.SessionDataService
import com.noproject.service.auth.TokenService
import net.codingwell.scalaguice.ScalaModule

class AuthModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[SessionDataService]
    bind[CustomerDataService]
    bind[TokenService]
  }
}
