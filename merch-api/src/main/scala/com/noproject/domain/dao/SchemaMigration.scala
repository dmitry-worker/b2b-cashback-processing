package com.noproject.domain.dao

import com.noproject.common.domain.{DbConfig, DefaultPersistence}
import com.noproject.common.logging.DefaultLogging
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

class SchemaMigration(dbConfig: DbConfig) extends DefaultLogging {
  import com.noproject.common.PipeOps._

  /**
    * Method is intended to be impure
    * It *should* collapse an app if failed.
    */
  def migrateBaseline(): Unit = {
    if (dbConfig.migrationEnabled) {
      try {
        flyway.migrate
      } catch {
        case e if e.getMessage.startsWith("Found non-empty schema") =>
          flyway.baseline()
          flyway.migrate()
      } finally {
        ds.close
      }
      logger.info("!!! db migration successfully completed !!!")
    } else {
      logger.info("!!! db migration disabled !!!")
    }
  }

  private lazy val ds: HikariDataSource = {
    val conf = DefaultPersistence.buildHikariConfig(dbConfig, None)
    conf.setMaximumPoolSize(1)
    new HikariDataSource(conf)
  }

  private lazy val resourcesLocation: List[String] =
    List(
      "filesystem:../sql", // for `sbt flyway` to work
      "filesystem:./sql" // to run from inside docker container
    )

  private lazy val flyway = {
    new Flyway()
      .<| (_.setDataSource(ds))
      .<| (_.setLocations(resourcesLocation: _*))
      .<| (_.setSchemas("public"))
      .<| (_.setIgnoreMissingMigrations(true))
      .<| (_.setOutOfOrder(true))
  }


}