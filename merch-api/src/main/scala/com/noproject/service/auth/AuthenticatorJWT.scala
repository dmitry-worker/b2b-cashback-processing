package com.noproject.service.auth

import java.time.Instant

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.noproject.common.config.ConfigProvider
import com.noproject.common.domain.model.customer.AccessRole
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.service.auth.Authenticator
import com.noproject.config.AuthConfig
import com.noproject.domain.model.customer.{CustomerSession, Session}
import com.noproject.domain.service.customer.SessionDataService
import io.circe.generic.auto._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Request}

import scala.util.control.NonFatal

abstract class AuthenticatorJWT(
  val authCP: ConfigProvider[AuthConfig]
, sessionDS:  SessionDataService
) extends Authenticator[CustomerSession] with CachedJWT with DefaultLogging {

  protected val accessRole: AccessRole

  val SESSION_ERROR = "Session validation error"
  val TOKEN_ERROR = "Token validation error"
  val EXPIRED_SESSION = "Session expired"
  val NO_SESSION = "Active session not found"
  val NO_TOKEN = "No token provided"
  val ACCESS_DENIED = "Access denied"
  val UNAUTHORIZED = "Unauthorized"

  def authUser(req: Request[IO]): IO[Either[String, CustomerSession]] = {
    def validateAccessRole(session: CustomerSession ) : Either[String, CustomerSession] = {
      if (session.customerRoles.contains(accessRole)) {
        Right(session)
      } else {
        Left(ACCESS_DENIED)
      }
    }

    def validateHeaderValue(headerValue: String): EitherT[ IO, String, Session ] = EitherT {
      val io = getCurrentJWT.flatMap( _.validate[IO, Session](headerValue) )
      val now = Instant.now

      io.redeem({ case NonFatal(ex) =>
        logger.error(s"Token validation error: ${ex.getMessage}")
        Left(TOKEN_ERROR)
      }, s => {
        if( now.isAfter( s.expiredAt ) )
          Left( EXPIRED_SESSION )
        else
          Right(s)
      })
    }

    def validateSession(token: String): EitherT[ IO, String, CustomerSession ] = EitherT {
      sessionDS.getUnexpired(token).redeem({
        case NonFatal(ex) =>
          logger.error(s"Session validation error: ${ex.getMessage}")
          Left(SESSION_ERROR)
      }, Either.fromOption( _, NO_SESSION ) )
    }

    def getHeader(req: Request[IO]): Either[String, Header] = {
      Either.fromOption(
        req.headers.get(CaseInsensitiveString("Authorization")),
        NO_TOKEN
      )
    }

    val eitherSession = for {
      header            <- EitherT.fromEither[IO]( getHeader( req ) )
      authorization     <- validateHeaderValue( header.value )
      knownSession      <- validateSession( authorization.sessionToken )
      authorizedSession <- EitherT.fromEither[IO]( validateAccessRole( knownSession ) )
    } yield authorizedSession

    eitherSession.value
  }

}
