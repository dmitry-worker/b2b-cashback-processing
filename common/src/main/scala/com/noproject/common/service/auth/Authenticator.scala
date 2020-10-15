package com.noproject.common.service.auth

import cats.effect.IO
import org.http4s.Request

trait Authenticator[A] {

  def authUser(req: Request[IO]): IO[Either[String, A]]

}
