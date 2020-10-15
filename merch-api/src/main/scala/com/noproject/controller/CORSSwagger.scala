package com.noproject.controller

import cats.Monad
import cats.effect.Sync
import io.swagger.util.Json
import org.http4s.headers.`Content-Type`
import org.http4s.rho.RhoRoutes
import org.http4s.rho.bits.PathAST.{PathMatch, TypedPath}
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.rho.swagger.models._
import org.http4s.{Header, MediaType}
import shapeless.HNil

import scala.reflect.runtime.universe._

object CORSSwagger {
  def apply[F[_]: Monad](implicit F: Sync[F], etag: WeakTypeTag[F[_]]): SwaggerSupport[F] = new CORSSwagger[F]()
}

class CORSSwagger[F[_]](implicit F: Sync[F], etag: WeakTypeTag[F[_]])
  extends SwaggerSupport[F] {
  override def createSwaggerRoute(
                          swagger: => Swagger,
                          apiPath: TypedPath[F, HNil] = TypedPath(PathMatch("swagger.json"))
                        ): RhoRoutes[F] = new RhoRoutes[F] {

    lazy val response: F[OK[String]] = {
      val fOk = Ok.apply(
        Json.mapper()
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(swagger.toJModel)
      )

      F.map(fOk) { ok =>
        ok.copy(resp = ok.resp.putHeaders(
          `Content-Type`(MediaType.application.json),
          Header("Access-Control-Allow-Origin","*")
          ))
      }
    }

    "Swagger documentation" ** GET / apiPath |>> (() => response)
  }
}
