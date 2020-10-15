package com.noproject.partner.coupilia.domain.model

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.{LocalDate, LocalDateTime}

import cats.free.Trampoline
import cats.implicits._
import io.circe._

import scala.util.Try

object CoupiliaCodec {

  def normalizeJsonObject(obj: JsonObject, f: String => String): JsonObject =
    JsonObject.fromIterable(
      obj.toList.collect {
        case (k, v) if !v.isString             => f(k) -> v
        case (k, v) if v.asString.get.nonEmpty => f(k) -> v
      }
    )

  def normalizeJson(json: Json, f: String => String): Trampoline[Json] =
    json.arrayOrObject(
      Trampoline.done(json),
      _.traverse(j => Trampoline.defer(normalizeJson(j, f))).map(Json.fromValues(_)),
      normalizeJsonObject(_, f).traverse(obj => Trampoline.defer(normalizeJson(obj, f))).map(Json.fromJsonObject)
    )

  def normalize(json: io.circe.Json): Json = normalizeJson(json, _.toLowerCase).run


  implicit val CommissionTypeEncoder: Encoder[CoupiliaCommissionType] = {
    Encoder.instance[CoupiliaCommissionType] {
      cct => Json.fromString(cct.entryName)
    }
  }

  implicit val CommissionTypeDecoder: Decoder[CoupiliaCommissionType] = {
    Decoder.instance[CoupiliaCommissionType] {
      json => json.as[String].right.map { s =>
        // crafty way to prepare "Flat Rate", etc. to match with enum
        val filtered = s.filter(_ != ' ')
        CoupiliaCommissionType.withName(filtered)
      }
    }
  }


  // LOCAL DATE
  val dateFormat: DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")

  implicit val LocalDateEncoder: Encoder[LocalDate] = Encoder.instance[LocalDate] {
    ld => Json.fromString(dateFormat.format(ld))
  }

  implicit val LocalDateDecoder: Decoder[LocalDate] = Decoder.instance[LocalDate] {
    json => json.as[String].right.map { s => LocalDate.from(dateFormat.parse(s)) }
  }


  // LOCAL DATE TIME
  val dateTimeFormat0: DateTimeFormatter = new java.time.format.DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd HH:mm:ss")
    .parseDefaulting(ChronoField.MILLI_OF_SECOND, 0)
    .toFormatter
  val dateTimeFormat1: DateTimeFormatter = new java.time.format.DateTimeFormatterBuilder()
    .appendPattern("MM/dd/yyyy hh:mm a")
    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
    .parseDefaulting(ChronoField.MILLI_OF_SECOND, 0)
    .toFormatter

  implicit val LocalDateTimeEncoder: Encoder[LocalDateTime] = Encoder.instance[LocalDateTime] {
    ld => Json.fromString(dateTimeFormat0.format(ld))
  }

  implicit val LocalDateTimeDecoder: Decoder[LocalDateTime] = Decoder.instance[LocalDateTime] {
    json => json.as[String].right.map { s =>
      val temporal =
        Try(dateTimeFormat0.parse(s))
          .getOrElse(dateTimeFormat1.parse(s))
      LocalDateTime.from(temporal)
    }
  }
  
}
