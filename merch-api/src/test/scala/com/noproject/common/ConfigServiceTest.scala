package com.noproject.common

import com.noproject.common.domain.dao.{ConfigDAO, DefaultPersistenceTest}
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.service.ConfigDataService
import com.noproject.domain.model.common.ConfigModel
import com.noproject.service.config.ConfigService
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.{Json, ParsingFailure}
import org.scalatest.{Matchers, WordSpec}

class ConfigServiceTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers {

  val confDAO = new ConfigDAO(xar)
  val confDS  = new ConfigDataService(confDAO)
  val service = new ConfigService(confDS)

  "ConfigServiceTest" should {

    "insert key and read it" in {
      case class Test1Config(q: String, e: String)

      val conf = ConfigModel("test1", Json.fromFields(
        Seq(
          "q"->Json.fromString("w"),
          "e"->Json.fromString("r")
        )
      ))
      confDS.deleteByKey(conf.key).unsafeRunSync()
      val res1 = confDS.insert(conf).unsafeRunSync()
      res1 shouldBe 1
      val item = service.getConfigItem[Test1Config]("test1").unsafeRunSync()
      item.q shouldBe "w"
      item.e shouldBe "r"
    }

    "insert app conf and read it" in {

      confDS.deleteAll.unsafeRunSync()

      val appConfigString =
        """
          |{
          |  "build": {
          |    "envMode": "Test",
          |    "version": "0.0.1"
          |  },
          |
          |  "auth": {
          |     "key": "F0p3LFStPqF2IdJXOq15A2fBWO11bgANHaHU6YEb",
          |     "enabled": true,
          |     "expirationMinutes": 20160,
          |     "sessionCacheSeconds": 10
          |  },
          |
          |  "http": {
          |    "port": 8500
          |  },
          |
          |  "db": {
          |    "jdbcUrl": "jdbc:postgresql://localhost:5432/postgres",
          |    "user": "postgres",
          |    "password": ""
          |  },
          |
          |  "offers": {
          |    "minCategory": 30,
          |    "maxCategory": 5,
          |    "baseUrl": "https://localhost:8500"
          |  }
          |}
        """.stripMargin

      val appConfigJson: Either[ParsingFailure, Json] = parse(appConfigString)
      val appConfigFields: Seq[(String, Json)] = appConfigJson match {
        case Left(ex) => throw ex
        case Right(json) =>
          json.asObject.toList.flatMap(obj => obj.toList)
      }

      appConfigFields.map(obj => confDS.insert(ConfigModel(obj._1, obj._2)).unsafeRunSync())
      appConfigFields should not be empty
    }

  }

}
