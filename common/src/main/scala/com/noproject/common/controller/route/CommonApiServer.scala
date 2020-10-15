package com.noproject.common.controller.route

import cats.data.Kleisli
import cats.effect.{ContextShift, IO, Timer}
import javax.inject.{Inject, Named}
import org.http4s.implicits._
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import org.http4s.rho.swagger.models.Info
import org.http4s.rho.swagger.syntax.io.createRhoMiddleware
import org.http4s.rho.{RhoMiddleware, RoutesBuilder}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig, Metrics}
import org.http4s.{Request, Response}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class CommonApiServer @Inject()(
  routes:            Set[Routing]
, pes:               PrometheusExportService[IO]
, @Named("httpPort") port: Int
) {
    implicit val cs: ContextShift[IO] = IO.contextShift(global)
    implicit val timer: Timer[IO] = IO.timer(global)

  // can be redefined in child class
  protected val corsConfig = CORSConfig(
    anyOrigin = true,
    anyMethod = false,
    allowedMethods = Some(Set("POST")),
    allowCredentials = true,
    maxAge = 1.day.toSeconds
  )

  // can be redefined in child class
  protected val rhoMiddleware: RhoMiddleware[IO] = createRhoMiddleware(
    apiInfo = Info(title = "Service API", version = "0.0.1"),
  )

  def runForever: IO[Unit] = {
    def externalApi(httpApp: Kleisli[IO, Request[IO], Response[IO]]) = {
      BlazeServerBuilder[IO].bindHttp(port, "0.0.0.0")
        .withHttpApp(httpApp)
        .withNio2(true)
        .withBufferSize(16535)
        .withDefaultSocketKeepAlive
        .withTcpNoDelay(true)
        .resource
    }

    val r = RoutesBuilder(rhoMiddleware(routes.toList.flatMap(_.getRoutes))).toRoutes()

    for {
      withMetrics <- Prometheus[IO](pes.collectorRegistry, "server").map(Metrics(_)(r))
      corsRoutes   =  CORS(withMetrics, corsConfig)
      extApp       =  Router("" -> corsRoutes).orNotFound
      resources    =  externalApi(extApp)
      result      <- resources.use(_ => IO.never)
    } yield result

  }
}
