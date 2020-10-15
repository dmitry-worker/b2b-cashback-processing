package com.noproject.common.domain.service

import cats.effect.IO
import com.noproject.common.domain.dao.{ConfigDAO, JsonConvertible}
import com.noproject.domain.model.common.ConfigModel
import doobie.Meta
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}

@Singleton
class ConfigDataService @Inject()(configDAO: ConfigDAO) {

  implicit val configModelMeta:Meta[ConfigModel] = JsonConvertible[ConfigModel]

  def getAll: IO[List[ConfigModel]] = configDAO.getAll

  def getByKey(key: String): IO[Option[ConfigModel]] = configDAO.findByKey(key)

  def insert(config: ConfigModel): IO[Int] = configDAO.insert(config)

  def deleteByKey(key: String): IO[Int] = configDAO.deleteByKey(key)

  def deleteAll: IO[Int] = configDAO.deleteAll()
}
