package com.noproject.common.security

import java.time.Instant

import cats.effect.IO
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.customer.AccessRole
import com.noproject.domain.model.customer.Session
import io.circe.generic.auto._
import org.scalatest.{Matchers, WordSpec}

import scala.util.Try

class JWTTest extends WordSpec with DefaultPersistenceTest with RandomValueGenerator with Matchers {

  "JWT" should {

    "validate long session" in {
      val jwt = new JWT("2128506", 10)
      val now = Instant.now
      val session = Session(0, None, randomStringUUID, now, now.plusSeconds(10 * 60))
      val token = jwt.build[IO, Session](session).unsafeRunSync()
      val validatedSession = jwt.validate[IO, Session](token.toEncodedString).unsafeRunSync()
      validatedSession.sessionToken shouldBe session.sessionToken
      validatedSession.startedAt shouldBe session.startedAt
      validatedSession.expiredAt shouldBe session.expiredAt
    }

    "validate expired session" in {
      val jwt = new JWT("2128506", 0)
      val now = Instant.now
      val session = Session(0, None, randomStringUUID, now, now)
      val token = jwt.build[IO, Session](session).unsafeRunSync()
      val validatedSession = Try(jwt.validate[IO, Session](token.toEncodedString).unsafeRunSync())
      validatedSession.isFailure shouldBe true
    }
  }
}
