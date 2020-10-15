package com.noproject.controller.route.admin

import cats.effect.IO
import com.noproject.common.service.auth.Authenticator
import com.noproject.controller.route.AuthenticatedRouting
import com.noproject.domain.model.customer.CustomerSession
import org.http4s.rho.bits.PathAST
import shapeless.HNil

abstract class AdminRouting(authenticator: Authenticator[CustomerSession]) extends AuthenticatedRouting(authenticator) {

  override val baseApiPath: PathAST.TypedPath[IO, HNil] = "api" / "admin"

}

