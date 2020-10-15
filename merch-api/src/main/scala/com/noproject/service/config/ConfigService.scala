package com.noproject.service.config

import cats.effect.IO
import com.noproject.common.Exceptions.ConfigException
import com.noproject.common.domain.service.ConfigDataService
import com.noproject.domain.model.common.ConfigModel
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import javax.inject.{Inject, Singleton}

@Singleton
class ConfigService @Inject()(configDataService: ConfigDataService) {

  def getConfigItem[A: Decoder](key: String): IO[A] = {
    def modelToA(model: ConfigModel): A = {
      model.value.as[A] match {
        case Left(_)    => throw ConfigException(Some(key))
        case Right(cfg) => cfg
      }
    }

    def optionToModel(optModel: Option[ConfigModel]): IO[A] = {
      optModel match {
        case Some(model) =>
          IO.pure[A](modelToA(model))
        case None =>
          val model = ConfigModel(key, Json.fromFields(Nil))
          configDataService.insert(model).map {
            _ => modelToA(model)
          }
      }
    }

    for {
      configOpt <- configDataService.getByKey(key)
      config    <- optionToModel(configOpt)
    } yield config
  }

}
