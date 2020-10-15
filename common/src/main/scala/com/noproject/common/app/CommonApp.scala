package com.noproject.common.app

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits._
import com.noproject.common.{ConfigUtil, Executors}
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.domain.DbConfig
import com.noproject.common.stream.{RabbitConfig, StreamEvent, StreamStart, StreamStop, TopicUtils}
import com.typesafe.config.ConfigFactory
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import fs2.concurrent.{SignallingRef, Topic}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.metrics.prometheus.PrometheusExportService

import scala.concurrent.duration._


trait CommonApp extends IOApp with ResourcefulApp {

  private   val confPath     = sys.env.getOrElse("common.config.path", "application.conf")
  protected lazy val parentConfig = ConfigFactory.parseResources(confPath).resolve()
  protected lazy val dbConfig     = DbConfig.fromConfig(parentConfig.getConfig("db"))

  protected val envMode: EnvironmentMode = {
    val strEnv = parentConfig.getString("build.envMode")
    EnvironmentMode.withName(strEnv)
  }

  protected def buildRabbit: Resource[IO, Fs2Rabbit[IO]] = {
    import RabbitConfig.decoder
    val confIO = IO.delay {
      val confObject = ConfigUtil.decodeUltimately[RabbitConfig](parentConfig, "rabbit")
      RabbitConfig.buildConfig(confObject)
    }
    Resource.liftF { confIO.flatMap(Fs2Rabbit[IO](_)) }
  }

  protected def buildHttpClient(maxConn: Int = 32, requestTimeoutSeconds: Int = 20, idleTimeoutSeconds: Int = 10): Resource[IO, Client[IO]] = {
    BlazeClientBuilder[IO](Executors.miscExec)
      .withMaxTotalConnections(maxConn)
      .withRequestTimeout(requestTimeoutSeconds seconds)
      .withBufferSize(16000)
      .withIdleTimeout(idleTimeoutSeconds seconds)
      .resource
  }

  protected def buildTopic[A]: Resource[IO, Topic[IO, StreamEvent[A]]] = TopicUtils.buildTopic

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      pes <- PrometheusExportService.build[IO]
      _   <- createResources(pes).use { runServices(_, pes) }
    } yield ExitCode.Success
  }

  private def runServices(resources: Resources, pes: PrometheusExportService[IO]): IO[_] = {
    createServices(resources, pes).parSequence
  }

  // use this to provide a service list
  protected def createServices(resources: Resources, pes: PrometheusExportService[IO]): List[IO[_]]

}
