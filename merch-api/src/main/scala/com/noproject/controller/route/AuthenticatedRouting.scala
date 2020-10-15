package com.noproject.controller.route

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import com.noproject.common.controller.route.Routing
import com.noproject.common.domain.model.customer.AccessRole
import com.noproject.common.service.auth.Authenticator
import com.noproject.domain.model.customer.CustomerSession
import org.http4s._
import org.http4s.server.AuthMiddleware

abstract class AuthenticatedRouting(authenticator: Authenticator[CustomerSession]) extends Routing {

  protected object Auth extends org.http4s.rho.AuthedContext[IO, CustomerSession]
  private[route] def authUser = Kleisli[IO, Request[IO], Either[String,CustomerSession]](authenticator.authUser)
  private def onFailure: AuthedRoutes[String, IO] = Kleisli ( _ => OptionT.liftF(IO.pure(Response[IO](Status.Unauthorized))) )
  private val authMiddleware: AuthMiddleware[IO, CustomerSession] = AuthMiddleware(authUser, onFailure)

  def service = authMiddleware.apply(Auth.toService(toRoutes()))

}

