package com.noproject.testserver

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.config.ConfigFactory
import org.http4s.implicits._
import org.http4s.rho.RoutesBuilder
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

object Testserver extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val confPath   = sys.env.getOrElse("default.config.path", "application.conf")
    val config     = ConfigFactory.parseResources(confPath).resolve()

    QueryLogBuilder.build.flatMap { qlog =>
      val ctrl   = new WebhookRoute(qlog)
      val routes = RoutesBuilder(ctrl.getRoutes).toRoutes()
      val router = Router("" -> routes).orNotFound
      BlazeServerBuilder[IO].bindHttp(config.getInt("http.port"), "0.0.0.0")
        .withHttpApp(router)
        .withNio2(true)
        .resource
        .use { _ => IO.never }
    }
  }

}
