package com.noproject.common.codec.json

import cats.implicits._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}


trait FuuidCodecs {

  implicit def fuuidDecoder: Decoder[FUUID] = new Decoder[FUUID] {
    final def apply(c: HCursor): Result[FUUID] = c.value.asString match {
      case Some(string) => FUUID.fromString(string).leftMap { e => DecodingFailure.fromThrowable(e, c.history) }
      case _            => Left(DecodingFailure("String", c.history))
    }
  }

  implicit def fuuidEncoder: Encoder[FUUID] = Encoder.instance( ee => Json.fromString(ee.toString) )

}

object FuuidCodecs extends FuuidCodecs