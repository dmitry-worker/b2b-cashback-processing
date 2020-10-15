package com.noproject.common.data.gen

import java.time.{Instant, LocalDate}

import com.noproject.common.domain.model.{Money, Percent}
import enumeratum.{Enum, EnumEntry}
import io.chrisdavenport.fuuid.FUUID
import shapeless.{::, Generic, HList, HNil}

trait Generator[R] {

  def apply: R

  def map[RR](f: R => RR): Generator[RR] = {
    val self = this
    new Generator[RR] {
      override def apply: RR = f ( self.apply )
    }
  }

}

object Generator extends RandomValueGenerator {

  def apply[A](implicit gen: Generator[A]): Generator[A] = gen

  implicit def fromHlist[A, R <: HList](
    implicit
      aux: Generic.Aux[A, R]
    , hlist: HlistGenerator[R]
  ): Generator[A] = {
    new Generator[A] {
      override def apply: A = aux.from(hlist.apply)
    }
  }

  implicit def optGen[T](implicit gent: Generator[T]): Generator[Option[T]] = {
    new Generator[Option[T]] {
      override def apply: Option[T] = randomOptBoolean.map( _ => gent.apply )
    }
  }

  implicit def listGen[T](implicit gent: Generator[T]): Generator[List[T]] = {
    new Generator[List[T]] {
      override def apply: List[T] = Range(0, randomInt(5) + 1).map( _ => gent.apply ).toList
    }
  }

  implicit def setGen[T](implicit gent: Generator[T]): Generator[Set[T]] = {
    new Generator[Set[T]] {
      override def apply: Set[T] = Range(0, randomInt(5) + 1).map( _ => gent.apply ).toSet
    }
  }

  implicit def enumGen[T <: EnumEntry](implicit t: Enum[T]): Generator[T] = {
    new Generator[T] {
      override def apply: T = randomOneOf[T](t.values)
    }
  }

  implicit def strGen:   Generator[String] = new Generator[String] { override def apply: String = randomString }
  implicit def boolGen:  Generator[Boolean] = new Generator[Boolean] { override def apply: Boolean = randomBoolean }
  implicit def intGen:   Generator[Int] = new Generator[Int] { override def apply: Int = randomInt }
  implicit def longGen:  Generator[Long] = new Generator[Long] { override def apply: Long = randomInt }
  implicit def doblGen:  Generator[Double] = new Generator[Double] { override def apply: Double = randomDouble }
  implicit def instGen:  Generator[Instant] = new Generator[Instant] { override def apply: Instant = randomInstant }
  implicit def dateGen:  Generator[LocalDate] = new Generator[LocalDate] { override def apply: LocalDate = randomLocalDate }
  implicit def deciGen:  Generator[BigDecimal] = new Generator[BigDecimal] { override def apply: BigDecimal = randomBigDecimal(2) }
  implicit def monyGen:  Generator[Money] = deciGen.map { Money(_) }
  implicit def percGen:  Generator[Percent] = deciGen.map { Percent(_) }
  implicit def uuidGen:  Generator[FUUID] = new Generator[FUUID] { override def apply: FUUID = randomUUID }

}

trait HlistGenerator[R <: HList] extends Generator[R] {

  def apply: R

}

object HlistGenerator {

  implicit def hnilGen: HlistGenerator[HNil] = {
    new HlistGenerator[HNil] {
      override def apply: HNil = HNil
    }
  }

  implicit def hlistGen[H, T <: HList](
    implicit
    hg: Generator[H]
  , tg: HlistGenerator[T]
  ): HlistGenerator[H :: T] = {
    new HlistGenerator[H :: T] {
      override def apply: H :: T = hg.apply :: tg.apply
    }
  }

}