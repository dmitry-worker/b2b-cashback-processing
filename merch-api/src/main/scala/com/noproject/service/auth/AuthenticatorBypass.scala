package com.noproject.service.auth

import cats.effect.IO
import com.noproject.common.service.auth.Authenticator
import com.noproject.domain.model.customer.CustomerSession
import javax.inject.Inject
import org.http4s.Request

class AuthenticatorBypass @Inject() extends Authenticator[CustomerSession] {

  override def authUser(req: Request[IO]): IO[Either[String, CustomerSession]] = {
    IO.pure( Right(CustomerSession.anonymous) )
  }

}
