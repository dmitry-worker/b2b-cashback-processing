package com.noproject.common.controller.route

import cats.effect.IO
import javax.inject.Named
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.server.middleware.authentication.BasicAuth.BasicAuthenticator

abstract class S2sRouting(
  @Named("s2sUser") user: String
, @Named("s2sPassword") password: String
) extends Http4sDsl[IO] {

  private val realm = ""

  private val authStore: BasicAuthenticator[IO, String] = (creds: BasicCredentials) => {
    val result = if (creds.username == user && creds.password == password) Some(creds.username) else None
    IO.pure(result)
  }

  protected val basicAuth: AuthMiddleware[IO, String] = BasicAuth(realm, authStore)

  def routes: HttpRoutes[IO]
}

