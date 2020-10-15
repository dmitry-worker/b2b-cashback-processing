package com.noproject.common.stream

import cats.data.NonEmptyList
import com.noproject.common.ConfigUtil
import com.typesafe.config.Config
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import io.circe.Decoder
import cats.syntax.functor._



object RabbitConfig {

  implicit val decoder: Decoder[RabbitConfig] = {
    import io.circe.generic.auto._
    Decoder[SimpleRabbitConfig].widen or Decoder[ClusterRabbitConfig].widen
  }

  def decodeAndBuildConfig(config: Config): Fs2RabbitConfig = {
    buildConfig(ConfigUtil.decodeUltimately[RabbitConfig](config))
  }

  def buildConfig(rc: RabbitConfig): Fs2RabbitConfig = {
    rc match {
      case src: SimpleRabbitConfig =>
        Fs2RabbitConfig(
          virtualHost         = "/"
        , host                = src.host //"127.0.0.1"
        , username            = rc.user //Some("rabbit")
        , password            = rc.password //Some("rabbit")
        , port                = src.port
        , ssl                 = false
        , connectionTimeout   = 3
        , requeueOnNack       = false
        , internalQueueSize   = Some(500)
        )
      case crc: ClusterRabbitConfig =>
        Fs2RabbitConfig(
          nodes               = NonEmptyList.fromListUnsafe(crc.nodes)
        , virtualHost         = "/"
        , connectionTimeout   = 3
        , ssl                 = false
        , username            = rc.user
        , password            = rc.password
        , requeueOnNack       = false
        , internalQueueSize   = Some(500)
        , automaticRecovery   = true
        )
    }
  }

}

sealed trait RabbitConfig {
  def user:     Option[String]
  def password: Option[String]
}

case class SimpleRabbitConfig(
  host:     String
, port:     Int
, user:     Option[String]
, password: Option[String]
) extends RabbitConfig

case class ClusterRabbitConfig(
  nodes:    List[Fs2RabbitNodeConfig]
, user:     Option[String]
, password: Option[String]
) extends RabbitConfig
