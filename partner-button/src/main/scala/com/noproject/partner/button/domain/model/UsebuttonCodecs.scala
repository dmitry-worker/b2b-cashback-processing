package com.noproject.partner.button.domain.model

import io.circe.{Decoder, DecodingFailure, Encoder, Json}

trait UsebuttonCodecs {
  implicit val centsDecoder: Decoder[UsebuttonCents] = _.as[Long].right.map { l => UsebuttonCents(l) }
  implicit val centsEncoder: Encoder[UsebuttonCents] = Encoder.instance( cents => Json.fromLong(cents.value) )
  implicit val categoryDecoder: Decoder[UsebuttonCategory] = Decoder.instance(cursor =>
    cursor.value match {
      case s if s.isString => Right(UsebuttonCategory(List(s.asString.get)))
      case l if l.isArray  => Right(UsebuttonCategory(l.asArray.get.map(_.asString.get).toList))
      case _ => Left(DecodingFailure(s"Failed to decode ${cursor.value} as UsebuttonCategory", cursor.history))
    })
}
