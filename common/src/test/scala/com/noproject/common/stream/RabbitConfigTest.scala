package com.noproject.common.stream

import com.noproject.common.ConfigUtil
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class RabbitConfigTest extends WordSpec with Matchers {

  val confSource =
    """
      |simple {
      |  host = "127.0.0.1"
      |  port = 5672
      |  user = "rabbit"
      |  password = "rabbit"
      |}
      |
      |cluster {
      |  nodes: [{
      |    host = "127.0.0.1"
      |    port = 5672
      |  },{
      |    host = "127.0.0.2"
      |    port = 5672
      |  },{
      |    host = "127.0.0.3"
      |    port = 5672
      |  }]
      |  user = "rabbit"
      |  password = "rabbit"
      |}
      |""".stripMargin


  "RabbitConfigTest" should {

    "decodeAndBuildConfig for single rabbit node" in {
      val config = ConfigFactory.parseString(confSource)
      val result = ConfigUtil.decodeUltimately[RabbitConfig](config.getConfig("simple"))
      result shouldBe a [SimpleRabbitConfig]
    }

    "decodeAndBuildConfig for rabbit cluster" in {
      val config = ConfigFactory.parseString(confSource)
      val result = ConfigUtil.decodeUltimately[RabbitConfig](config.getConfig("cluster"))
      result shouldBe a [ClusterRabbitConfig]
    }

  }
}
