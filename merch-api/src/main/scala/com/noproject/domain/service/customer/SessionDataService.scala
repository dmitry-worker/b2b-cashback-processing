package com.noproject.domain.service.customer

import java.time.{Clock, Instant}

import cats.effect.IO
import com.noproject.common.config.ConfigProvider
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.config.AuthConfig
import com.noproject.domain.dao.customer.{CustomerSessionDAO, SessionDAO}
import com.noproject.domain.model.customer.{CustomerSession, Session}
import javax.inject.{Inject, Singleton}


@Singleton
class SessionDataService @Inject()(
  sessionDAO: SessionDAO
, customerSessionDAO:  CustomerSessionDAO
, authConfigProvider:  ConfigProvider[AuthConfig]
, clock: Clock
) extends RandomValueGenerator {

  def insert(userId: Option[String] ): IO[CustomerSession] = {
    val now = Instant.now
    val token = randomStringUUID
    for {
      ac     <- authConfigProvider.getConfig
      exp     = now.plusSeconds(60 * ac.expirationMinutes)
      session = Session(0, userId, token, now, exp)
      result <- customerSessionDAO.insert(session)
    } yield result

  }

  def get(token: String): IO[Option[CustomerSession]] = {
    customerSessionDAO.getByToken( token )
  }

  def getUnexpired(token: String): IO[Option[CustomerSession]] = {
    get(token).map { ocs =>
      ocs.filter(_.notExpired(Instant.now))
    }
  }

  def expire(token: String): IO[Int] = {
    sessionDAO.updateExpirationByToken(token, clock.instant())
  }
}
