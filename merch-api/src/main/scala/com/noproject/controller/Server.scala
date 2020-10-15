package com.noproject.controller

import cats.data.{Kleisli, OptionT}
import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.controller.route.{MonitoredRouting, Routing}
import com.noproject.controller.route.AuthenticatedRouting
import javax.inject.{Inject, Named, Singleton}
import org.http4s.implicits._
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import org.http4s.rho.swagger.models.Info
import org.http4s.rho.{RhoRoutes, RoutesBuilder}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig, Metrics}
import org.http4s.{HttpRoutes, Request, Response}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._


@Singleton
class Server @Inject()(
  routeSet:     Set[Routing]
//, adminRouteSet: Set[LegacyAdminRouting]
//, mwRouteSet:  Set[AuthenticatedRouting]
, pes:          PrometheusExportService[IO]
, @Named("appVersion") version: String
, @Named("httpPort") httpPort: Int
, @Named("prometheusPort") prometheusPort: Int
) {

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  private def createSwaggerRoute: RhoRoutes[IO] = {
    val srcRoutes = routeSet.toList.flatMap(_.getRoutes)
    val swagger = CORSSwagger[IO].createSwagger(
      apiInfo = Info(title = "Common Offers API", version = version)
    )(srcRoutes)
    CORSSwagger[IO].createSwaggerRoute(swagger)
  }

  private def createMonitoredRoutes(rawRoutes: Iterable[Routing], preprefix: String = ""): IO[HttpRoutes[IO]] = {
    import cats.implicits._

    val (monitored, other) = rawRoutes.partition {
      case m: MonitoredRouting => true
      case o                   => false
    }

    val monitoredRoutes = monitored.map {
      case m: MonitoredRouting =>
        val rs      = m.getRoutes
        val prefix  = preprefix + m.monitoringPrefix
        val http    = RoutesBuilder(rs).toRoutes()
        Prometheus[IO](pes.collectorRegistry, prefix).map(Metrics(_)(http))
      case _ =>
        throw new IllegalStateException("Should never happen.")
    }

    val otherRoutes = other.map {
      case m: MonitoredRouting =>
        throw new IllegalStateException("Should never happen.")
      case r =>
        r.getRoutes
    }.fold(Nil)(_ ++ _)


    for {
      monitoredHttpApp <- monitoredRoutes.toList.sequence.map { _.fold(HttpRoutes.liftF[IO](OptionT.none))(_ <+> _) }
      otherHttpRoutes   = RoutesBuilder(otherRoutes).toRoutes()
      otherHttpApp     <- Prometheus[IO](pes.collectorRegistry, preprefix + "other").map(Metrics(_)(otherHttpRoutes))
    } yield {
      monitoredHttpApp <+> otherHttpApp
    }

  }

  private val corsConfig = CORSConfig(
    anyOrigin = true,
    anyMethod = true,
    allowCredentials = true,
    maxAge = 1.day.toSeconds
  )

  def start: IO[Unit] = {

    import cats.implicits._

    def externalApi(httpApp: Kleisli[IO, Request[IO], Response[IO]]) = {
      BlazeServerBuilder[IO].bindHttp(httpPort, "0.0.0.0")
        .withHttpApp(httpApp)
        .withNio2(false)
        .withBufferSize(16535)
        .withDefaultSocketKeepAlive
        .withTcpNoDelay(true)
        .resource
    }

    def internalApi(httpApp: Kleisli[IO, Request[IO], Response[IO]]) = {
      BlazeServerBuilder[IO].bindHttp(prometheusPort, "0.0.0.0")
        .withHttpApp(httpApp)
        .withNio2(false)
        .withBufferSize(16535)
        .withDefaultSocketKeepAlive
        .withTcpNoDelay(true)
        .resource
    }

    val (authRouteSet, anonRouteSet) = routeSet.partition(_.isInstanceOf[AuthenticatedRouting])

    val middlewaredRoutes = authRouteSet.toList.map(_.asInstanceOf[AuthenticatedRouting].service).fold(HttpRoutes.empty[IO])(_ <+> _)

    for {
      userRoutes    <- createMonitoredRoutes(anonRouteSet)
      swaggerRoutes =  createSwaggerRoute.toRoutes(identity)
      corsRoutes    =  CORS(userRoutes <+> swaggerRoutes <+> middlewaredRoutes, corsConfig)
      extApp        =  Router("" -> corsRoutes).orNotFound
      intApp        =  Router("" -> pes.routes).orNotFound
      resources     =  for {
                        ext <- externalApi(extApp)
                        int <- internalApi(intApp)
                       } yield (ext, int)
      result      <- resources.use(_ => IO.never)
    } yield result

  }

}
