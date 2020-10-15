package com.noproject.common.codec.json

import com.noproject.common.domain.model.{Money, Percent}
import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder, Json}


trait ValueClassCodecs {

  implicit val moneyDecoder: Decoder[Money] = Decoder.decodeBigDecimal.map(bd => Money(bd))
  implicit val moneyEncoder: Encoder[Money] = Encoder.instance(money => Json.fromBigDecimal(money.amount) )

  implicit val percentDecoder: Decoder[Percent] = Decoder.decodeBigDecimal.map(bd => Percent(bd))
  implicit val percentEncoder: Encoder[Percent] = Encoder.instance(pct => Json.fromBigDecimal(pct.amount) )

}
