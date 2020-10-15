package com.noproject.common.controller.route

import cats.effect.IO
import com.noproject.common.Exceptions.NotFoundException
import com.noproject.common.controller.dto.CommonError
import io.circe.generic.auto._
import org.http4s.circe.jsonEncoderOf
import org.http4s.rho.bits.PathAST
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.rho.{Result, RhoRoutes}
import org.http4s.{EntityEncoder, Uri}
import shapeless.HNil

import scala.util.control.NonFatal

trait MonitoredRouting {
  this:Routing =>

  def monitoringPrefix: String

}
