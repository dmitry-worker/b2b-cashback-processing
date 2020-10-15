package com.noproject.common.codec.json

import cats.Show
import io.circe._
import io.circe.syntax._

import scala.language.implicitConversions
import scala.reflect.runtime.universe._

object JsonShow {

  object NoSpaces {
    def apply[T](implicit t: TypeTag[T], et: Encoder[T], dt: Decoder[T]): Show[T] =  {
      Show.show(src => src.asJson.noSpaces)
    }
  }

  object TwoSpaces {
    def apply[T](implicit t: TypeTag[T], et: Encoder[T], dt: Decoder[T]): Show[T] =  {
      Show.show(src => src.asJson.spaces2)
    }
  }

}
