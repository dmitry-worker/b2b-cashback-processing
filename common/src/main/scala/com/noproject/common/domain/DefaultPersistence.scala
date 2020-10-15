package com.noproject.common.domain

import java.util.TimeZone

import cats.effect.{ContextShift, IO, Resource}
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import doobie.hikari._
import io.prometheus.client.CollectorRegistry

object DefaultPersistence {

  val DataSource = "org.postgresql.ds.PGSimpleDataSource"

  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

  def buildHikariConfig(dbConfig: DbConfig, metrics: Option[CollectorRegistry]): HikariConfig = {
    val hikariConfig = new HikariConfig()
    metrics.foreach { m =>
      val factory = new PrometheusMetricsTrackerFactory(m)
      hikariConfig.setMetricsTrackerFactory(factory)
    }
    hikariConfig.setDataSourceClassName(DataSource)
    hikariConfig.addDataSourceProperty("url", dbConfig.jdbcUrl)
    hikariConfig.addDataSourceProperty("user", dbConfig.user)
    hikariConfig.addDataSourceProperty("password", dbConfig.password)
    hikariConfig.setMaximumPoolSize(10)
    hikariConfig.setConnectionTimeout(5000)
    hikariConfig.setValidationTimeout(2000)
    hikariConfig
  }


  def transactor(dbConfig: DbConfig, metrics: Option[CollectorRegistry])(implicit cs: ContextShift[IO]): Resource[IO, DefaultPersistence] = {
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      te <- ExecutionContexts.cachedThreadPool[IO]    // our transaction EC
      cfg = buildHikariConfig(dbConfig, metrics)
      xa <- HikariTransactor.fromHikariConfig[IO](cfg, ce, te)
    } yield DefaultPersistence(xa)
  }

}

case class DefaultPersistence(xar: Transactor[IO])
