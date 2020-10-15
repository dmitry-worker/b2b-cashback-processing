package com.noproject.common.domain.dao

import doobie.util.Meta
import io.circe._
import io.circe.parser._
import org.postgresql.util.PGobject

import scala.language.implicitConversions
import scala.reflect.runtime.universe._

object JsonConvertible {

  import com.noproject.common.PipeOps._

  def apply[T](implicit t: TypeTag[T], et: Encoder[T], dt: Decoder[T]): Meta[T] =  {
    Meta.Advanced.other[PGobject]("json").timap[T](
      a => parse(a.getValue).flatMap(_.as[T]).left.map[T](e => sys.error(e.toString)).merge
    )(
      a => new PGobject <| (_.setType("json")) <| (_.setValue(et(a).noSpaces))
    )
  }

}
