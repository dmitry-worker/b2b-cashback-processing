package com.noproject.common.codec.csv

import java.time.Instant

import com.noproject.common.codec.csv.CsvEscapeOption.{All, Sep}
import enumeratum.{Enum, EnumEntry}
import io.chrisdavenport.fuuid.FUUID
import io.circe.Json
import shapeless.labelled.FieldType
import shapeless.{::, Generic, HList, HNil, LabelledGeneric, Witness}


trait CsvCodec[A] {
  def apply(src: A): String
}

trait ProductCsvCodec[A] {
  def apply(fmt: CsvFormat): CsvWriter[A]
}

abstract class CsvWriter[A] {
  def header: String
  def apply(src: A): String
}


trait CsvCodecs {

  val NEWLINE = "\r\n"

  def apply[A](implicit codec: ProductCsvCodec[A]): ProductCsvCodec[A] = codec

  implicit def hnilCodec: ProductCsvCodec[HNil] = fmt => new CsvWriter[HNil] {
    override def header: String = ""
    override def apply(src: HNil): String = NEWLINE
  }

  implicit def hlistCodec[H, K <: Symbol, T <: HList](implicit hc: CsvCodec[H],  tc: ProductCsvCodec[T], k: Witness.Aux[K]): ProductCsvCodec[FieldType[K, H] :: T] = fmt => {
    val tailWriter = tc.apply(fmt)
    new CsvWriter[FieldType[K, H] :: T] {
      override def header: String = {
        if (fmt allows k.value.name) {
          if (tailWriter.header.isEmpty) k.value.name + NEWLINE
          else k.value.name + fmt.separator.toString + tailWriter.header
        } else {
          tailWriter.header
        }
      }
      override def apply(src: FieldType[K, H] :: T): String = {
        val tail = tailWriter.apply(src.tail)
        if (fmt allows k.value.name) {
          val result = format0(hc.apply(src.head), fmt)
          if (src.tail != HNil) result + fmt.separator.toString + tail
          else result + tail
        } else {
          tail
        }
      }
    }
  }

  implicit def genericCodec[A, L <: HList](implicit gen: LabelledGeneric.Aux[A, L], pcc: ProductCsvCodec[L]): ProductCsvCodec[A] = fmt => {
    val tailWriter = pcc.apply(fmt)
    new CsvWriter[A] {
      override def header: String = tailWriter.header
      override def apply(src: A): String = tailWriter.apply( gen.to(src) )
    }
  }

  implicit def optionCodec[A](implicit vc: CsvCodec[A]): CsvCodec[Option[A]] = {
    opt => opt.map(a => vc.apply(a)).getOrElse("")
  }



  implicit val stringCodec: CsvCodec[String] = (s) => s
  implicit val boolCodec: CsvCodec[Boolean] = (b) => b.toString
  implicit val intCodec: CsvCodec[Int] = (i) => i.toString
  implicit val longCodec: CsvCodec[Long] = (l) => l.toString
  implicit val instantCodec: CsvCodec[Instant] = (i) => i.toString
  implicit val decimalCodec: CsvCodec[BigDecimal] = (i) => i.toString
  implicit val jsonCodec: CsvCodec[Json] = (_) => "{...}"
  implicit val fuuidCodec: CsvCodec[FUUID] = (i) => i.show
  implicit def enumCodec[T <: EnumEntry](implicit t: Enum[T]): CsvCodec[T] = { (t: T) => t.entryName }

  private def format0(head: String, fmt: CsvFormat): String = fmt.escape match {
    case Sep if !head.contains(fmt.separator.toString) => head
    case _   if head.isEmpty                           => head
    case _                                             => "\"" + head + "\""
  }

}

object CsvCodecs extends CsvCodecs