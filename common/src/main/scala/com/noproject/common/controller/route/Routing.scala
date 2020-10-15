package com.noproject.common.controller.route

import cats.effect.IO
import com.noproject.common.Exceptions.NotFoundException
import com.noproject.common.controller.dto.CommonError
import io.circe.generic.auto._
import org.http4s.Charset.`UTF-8`
import org.http4s.circe.jsonEncoderOf
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.rho.Result.BaseResult
import org.http4s.rho.bits.PathAST
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.rho.{Result, RhoRoutes}
import org.http4s.{EntityEncoder, MediaType, Uri}
import org.log4s.Logger
import shapeless.HNil

import scala.util.control.NonFatal

trait Routing extends RhoRoutes[IO] with SwaggerSyntax[IO] {
  val baseApiPath: PathAST.TypedPath[IO, HNil] = "api"

  implicit val errorEncoder: EntityEncoder[IO, CommonError] = jsonEncoderOf[IO, CommonError]

  type CommonResponse = BaseResult[IO]

  def redeemAndSuccess[A](io: IO[A])(implicit e: EntityEncoder[IO, A]): IO[CommonResponse] = io.redeemWith(
    responseRecover, responseSuccess
  )

  def redeemAndCsv[A](io: IO[A])(implicit e: EntityEncoder[IO, A]): IO[CommonResponse] = io.redeemWith(
    responseRecover, responseCsv
  )


  def redeemAndRedirect[A](io: IO[String]): IO[CommonResponse] = io.redeemWith(
    responseRecover, responseRedirect
  )

  def responseRecover[A]: Throwable => IO[CommonResponse] = {
    case ex : NotFoundException =>
      NotFound(CommonError(message = ex.getMessage))
    case NonFatal(ex) =>
      logger.error(s"Default redeem: ${ex.getMessage}")
      BadRequest(CommonError(message = "Bad request"))
  }

  def responseSuccess[A](implicit e: EntityEncoder[IO, A]): A => IO[CommonResponse] = {
    body => Ok(body)
  }

  def responseRedirect[A]: String => IO[CommonResponse] = { url =>
    logger.info(s"Redirect uri is $url")
    Uri.fromString(url) match {
      case Left(error) =>
        logger.error(s"$error")
        BadRequest(CommonError(message = "Invalid redirect url"))
      case Right(uri)  =>
        Found(uri)
    }
  }

  def responseCsv[A](implicit e: EntityEncoder[IO, A]): A => IO[CommonResponse] = {
    body => Ok(body).map { _.putHeaders(
      `Content-Disposition`("attachment", Map("filename" -> "report.csv"))
    , `Content-Type`(MediaType.text.csv, charset = Some(`UTF-8`))
    )}
  }
}
