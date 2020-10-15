package com.noproject.controller.route

import cats.effect.IO
import com.noproject.common.controller.route.{MonitoredRouting, Routing}
import com.noproject.controller.dto.auth.{TokenRequest, TokenResponse}
import com.noproject.service.auth.TokenService
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder, Request}


@Singleton
class AuthRoute @Inject()(tokenService: TokenService) extends Routing with MonitoredRouting {

  override def monitoringPrefix: String = "auth"

  val tokenApiPath = baseApiPath / "v1" / "auth" / "token"

  implicit val reqDec: EntityDecoder[IO, TokenRequest] = jsonOf[IO, TokenRequest]
  implicit val resDec: EntityEncoder[IO, TokenResponse] = jsonEncoderOf[IO, TokenResponse]

  "Get new token" **
  POST / tokenApiPath ^ jsonOf[IO, TokenRequest] |>> { (req: Request[IO], treq: TokenRequest) =>
    val io = for {
      res      <- tokenService.tokenize(treq)
      tres      = TokenResponse(res._1.toEncodedString, res._2)
    } yield tres
    redeemAndSuccess(io)
  }

  "Exchange token" **
  GET / tokenApiPath / "exchange" / pathVar[String]("token") |>> { (req: Request[IO], token: String) =>
    val io = tokenService.exchange(token).map {
      case (tok, exp) => TokenResponse(tok.toEncodedString, exp)
    }
    redeemAndSuccess(io)
  }

}
