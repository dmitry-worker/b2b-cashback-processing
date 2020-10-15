package com.noproject.common.controller.route

import cats.effect.{ContextShift, IO, Timer}
import javax.inject.{Inject, Named}
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext.global

class CommonPrometheusServer @Inject()(
  @Named("prometheusPort") port: Int
) {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  def runForever(routes: HttpRoutes[IO]): IO[Unit] = {
    BlazeServerBuilder[IO].bindHttp(port, "0.0.0.0")
      .withHttpApp(Router("" -> routes).orNotFound)
      .withNio2(false)
      .withBufferSize(16535)
      .withDefaultSocketKeepAlive
      .withTcpNoDelay(true)
      .resource
      .use { _ => IO.never }
  }
}
