package com.noproject.modules

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}
import com.noproject.common.ConfigUtil
import com.noproject.common.config._
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.ConfigDAO
import com.noproject.common.domain.service.ConfigDataService
import com.noproject.common.service.auth.Authenticator
import com.noproject.config._
import com.noproject.domain.model.customer.CustomerSession
import com.noproject.service.auth.{AuthenticatorAdmin, AuthenticatorBypass, AuthenticatorJWT, AuthenticatorUser}
import com.noproject.service.config.ConfigService
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import io.circe.generic.auto._

class ConfigModule(persistence: DefaultPersistence, config: Config)(implicit cs: ContextShift[IO]) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    // TODO refactor ConfigModule, use ConfigProvider[T] instead of ConfigService
    val service = new ConfigService(new ConfigDataService(new ConfigDAO(persistence)))

    val schedulerConfig = service
      .getConfigItem[SchedulerConfig]("scheduler")
      .unsafeRunSync()

    bind[Config].toInstance(config)
    bind[SchedulerConfig].toInstance(schedulerConfig)


    val authEnabled = config.getBoolean("auth.enabled")
    bind[Boolean].annotatedWith(Names.named("authEnabled")).toInstance(authEnabled)

    val appVersion = config.getString("build.version")
    bind[String].annotatedWith(Names.named("appVersion")).toInstance(appVersion)

    if (authEnabled) {
      bind(new TypeLiteral[Authenticator[CustomerSession]](){})
        .annotatedWith(Names.named("customer"))
        .to(classOf[AuthenticatorUser])
      bind(new TypeLiteral[Authenticator[CustomerSession]](){})
        .annotatedWith(Names.named("admin"))
        .to(classOf[AuthenticatorAdmin])
    } else {
      bind(new TypeLiteral[Authenticator[CustomerSession]](){})
        .annotatedWith(Names.named("customer"))
        .to(classOf[AuthenticatorBypass])
      bind(new TypeLiteral[Authenticator[CustomerSession]](){})
        .annotatedWith(Names.named("admin"))
        .to(classOf[AuthenticatorBypass])
    }


    bind(new TypeLiteral[Concurrent[IO]](){}).toInstance(implicitly[Concurrent[IO]])
    bind(new TypeLiteral[ConfigProvider[OffersConfig]](){}).to(classOf[OffersConfigProvider])
    bind(new TypeLiteral[ConfigProvider[AuthConfig]](){}).to(classOf[AuthConfigProvider])
    bind(new TypeLiteral[ConfigProvider[S2sConfig]](){}).to(classOf[S2sConfigProvider])

    bind[Int].annotatedWith(Names.named("httpPort")).toInstance(config.getInt("http.port"))
    bind[Int].annotatedWith(Names.named("prometheusPort")).toInstance(config.getInt("prometheus.port"))
  }

}
