package com.noproject.common.domain.dao

import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.domain.model.common.ConfigModel
import doobie._
import io.circe.Json
import javax.inject.{Inject, Singleton}

import scala.language.implicitConversions

@Singleton
class ConfigDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override val tableName = "config"

  override val keyFieldList = Nil

  override val allFieldList = List(
     "key",
     "value"
  )

  def getAll: IO[List[ConfigModel]] = {
    val q = s"select $allFields from $tableName"
    Query[Unit, ConfigModel](q, None).toQuery0(()).to[List]
  }

  def findByKey(key: String): IO[Option[ConfigModel]] = {
    val q = s"select $allFields from $tableName where key = ?"
    Query[String, ConfigModel](q, None).toQuery0(key).option
  }

  def insert(conf: ConfigModel): IO[Int] = {
    val sql = s"insert into $tableName ($valFields) values ($valHoles)"
    Update[(String, Json)](sql).toUpdate0(conf.key, conf.value).run
  }

  def deleteByKey(key: String): IO[Int] = {
    val sql = s"delete from $tableName where key = ?"
    Update[String](sql).toUpdate0(key).run
  }

}
