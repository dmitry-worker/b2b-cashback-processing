package com.noproject.common.codec.json

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import io.circe.Json.{JNumber, JString}
import io.circe._

import scala.util.{Failure, Success, Try}


trait InstantCodecs {

  private lazy val fmt = DateTimeFormatter.ISO_INSTANT

  private def tryDecode(tryExpr: Instant, cursor: HCursor): Decoder.Result[Instant] = {
    Try(tryExpr) match {
      case Failure(ex)    => Left(DecodingFailure(ex.getMessage, cursor.history))
      case Success(value) => Right(value)
    }
  }

  implicit def instantDecoder: Decoder[Instant] = Decoder.instance { cursor: HCursor =>
    cursor.value match {
      case s if s.isString => tryDecode(Instant.from( fmt.parse( s.as[String].right.get )), cursor)
      case l if l.isNumber => tryDecode(Instant.ofEpochMilli( l.as[Long].right.get ), cursor)
      case _ => Left(DecodingFailure(s"Failed to decode ${cursor.value} as Instant", cursor.history))
    }
  }

  def formatDate(s: Instant) = fmt.format(s)

  implicit def instantEncoder: Encoder[Instant] = Encoder.instance( s => Json.fromString(formatDate(s)))

}

object InstantCodecs extends InstantCodecs
