package com.noproject.common.codec.json

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder, Json}



trait EnumerationCodecs {

  implicit def enumDecoder[T <: EnumEntry](implicit t:Enum[T]): Decoder[T] = Decoder.decodeString.map(t.withName)
  implicit def enumEncoder[T <: EnumEntry]: Encoder[T] = Encoder.instance( ee => Json.fromString(ee.entryName) )

}
