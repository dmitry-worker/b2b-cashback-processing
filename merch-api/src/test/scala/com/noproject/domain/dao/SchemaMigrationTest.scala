package com.noproject.domain.dao

import com.noproject.common.domain.DbConfig
import org.scalatest.{DoNotDiscover, WordSpec}

@DoNotDiscover
class SchemaMigrationTest extends WordSpec {

  lazy val dbconf = DbConfig(
    jdbcUrl = "jdbc:postgresql://localhost:5432/test"
  , user  = "postgres"
  , password  = ""
  , migrationEnabled = true
  )

  lazy val migration = new SchemaMigration(dbconf)

  "SchemaMigrationTest" should {

    "migrate" in {
      migration.migrateBaseline
    }

  }
}
