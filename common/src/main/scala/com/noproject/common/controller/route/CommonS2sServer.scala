package com.noproject.common.controller.route

import cats.effect.{ContextShift, IO, Timer}
import javax.inject.{Inject, Named}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext.global

class CommonS2sServer @Inject()(
  s2sRoutes:        Set[S2sRouting]
, @Named("s2sPort") port: Int
) extends Http4sDsl[IO] {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  def runForever: IO[Unit] = {

    import cats.implicits._
    val routes: HttpRoutes[IO] = s2sRoutes.map(_.routes).toList.fold(HttpRoutes.empty[IO])(_ <+> _)

    def externalApi = {
      BlazeServerBuilder[IO].bindHttp(port, "0.0.0.0")
        .withHttpApp(Router("" -> routes).orNotFound)
        .withNio2(true)
        .withBufferSize(16535)
        .withDefaultSocketKeepAlive
        .withTcpNoDelay(true)
        .resource
    }

    externalApi.use(_ => IO.never)
  }
}
