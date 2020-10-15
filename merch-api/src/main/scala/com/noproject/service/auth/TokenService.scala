package com.noproject.service.auth

import java.time.Instant

import cats.effect.IO
import com.noproject.common.Exceptions.SessionNotFoundException
import com.noproject.common.config.ConfigProvider
import com.noproject.common.domain.model.customer._
import com.noproject.common.domain.service.CustomerDataService
import com.noproject.config.AuthConfig
import com.noproject.controller.dto.auth.TokenRequest
import com.noproject.domain.model.customer.{CustomerSession, Session}
import com.noproject.domain.service.customer.SessionDataService
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA512

@Singleton
class TokenService @Inject()(
  sessionDataService: SessionDataService
, customerDataService: CustomerDataService
, val authCP: ConfigProvider[AuthConfig]
) extends CachedJWT {

  private def validateCredentials(tr: TokenRequest): IO[Customer] = {
    for {
      customer  <- customerDataService.getByKey(tr.apiKey)
      validated <- CustomerUtil.checkHash(tr.customerName, tr.apiKey, tr.apiSecret, customer.hash)
    } yield {
      if (validated) {
        customer
      } else {
        throw new RuntimeException(s"Customer info validation error.")
      }
    }
  }

  private def insertNewSession(userId: Option[String]): IO[CustomerSession] = {
    sessionDataService.insert(userId)
  }

  private def exchangeSession(token: String): IO[CustomerSession] = {
    def expire(cs: CustomerSession): IO[CustomerSession] = {
      for {
        s <- sessionDataService.insert(cs.customerName)
        _ <- sessionDataService.expire(cs.session.sessionToken)
      } yield s
    }

    sessionDataService.getUnexpired(token).flatMap {
      case Some(cs) => expire(cs)
      case _        => throw SessionNotFoundException(token)
    }
  }

  private def createToken(session: Session): IO[JWTMac[HMACSHA512]] = {
    for {
      actualJWT   <- getCurrentJWT
      result      <- actualJWT.build[IO, Session](session)
    } yield result
  }

  def tokenize(tr: TokenRequest): IO[(JWTMac[HMACSHA512], Instant)] = {
    for {
      customer <- validateCredentials(tr)
      cs       <- insertNewSession(Some(customer.name))
      token    <- createToken(cs.session)
    } yield {
      (token, cs.session.expiredAt)
    }
  }

  def exchange(oldToken: String): IO[(JWTMac[HMACSHA512], Instant)] = {
    for {
      jwt     <- getCurrentJWT
      data    <- jwt.validate[IO, Session](oldToken)
      cs      <- exchangeSession(data.sessionToken)
      token   <- createToken(cs.session)
    } yield {
      (token, cs.session.expiredAt)
    }
  }

}
