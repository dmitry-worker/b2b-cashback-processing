package com.noproject.modules

import com.google.inject.AbstractModule
import com.noproject.common.domain.DefaultPersistence
import net.codingwell.scalaguice.ScalaModule

class DomainModule(pers: DefaultPersistence) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    bind[DefaultPersistence].toInstance(pers)
  }
}
