package com.noproject.controller.route.admin

import java.time.Instant

import cats.effect.IO
import cats.implicits._
import com.noproject.common.codec.json.InstantCodecs
import com.noproject.common.config.ConfigProvider
import com.noproject.common.controller.route.MonitoredRouting
import com.noproject.common.domain.model.customer.AccessRole
import com.noproject.common.service.auth.Authenticator
import com.noproject.config.S2sConfig
import com.noproject.domain.model.customer.CustomerSession
import io.circe.generic.auto._
import javax.inject.{Inject, Named, Singleton}
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.rho.bits.PathAST
import shapeless.HNil

@Singleton
class TxnRefreshRoute @Inject()(
  @Named("admin")
  authenticator: Authenticator[CustomerSession]
, s2sCP: ConfigProvider[S2sConfig]
, client: Client[IO]
) extends AdminRouting(authenticator) with InstantCodecs {

  private val path: PathAST.TypedPath[IO, HNil] = baseApiPath / "v1" / "txn" / "refresh"
  private val s2sPath = "/api/internal/txn/refresh"

  private def doRequest(cfg: S2sConfig, partner: String, from: Option[Instant], to: Option[Instant]) = {

    val partnerOpt = cfg.urls.get(partner)
    if (partnerOpt.isEmpty)
      IO.raiseError(new RuntimeException(s"S2S url for $partner not found"))
    else {
      val uri = Uri
        .unsafeFromString(partnerOpt.get)
        .withPath(s2sPath)
        .withOptionQueryParam("from", from.map(_.toEpochMilli))
        .withOptionQueryParam("to", to.map(_.toEpochMilli))

      val headers = Headers.of(Authorization(BasicCredentials(cfg.user, cfg.password)))

      val request: Request[IO] = Request[IO](method = GET, uri = uri, headers = headers)

      client.fetch(request) { res: Response[IO] =>
        res.status match {
          case Status.Ok => IO.unit
          case s =>
            res.bodyAsText.compile.string.flatMap { responseText =>
              IO.raiseError(new RuntimeException(s"S2S request to $partner failed: status ${s.code}, message $responseText"))
            }
        }
      }
    }
  }

  GET / path / pathVar[String] +? (param[Option[Instant]]("from") & param[Option[Instant]]("to")) >>> Auth.auth |>> {
  (req: Request[IO], partner: String, from: Option[Instant], to: Option[Instant], session: CustomerSession) =>
      logger.info(s"GET refresh $partner txns by admin ${session.customerName}")
      s2sCP.getConfig.flatMap(cfg => doRequest(cfg, partner, from, to))
  }

}
