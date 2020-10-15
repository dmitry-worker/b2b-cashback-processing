package com.noproject.common.data.gen

import java.time.{Instant, LocalDate}

import cats.effect.IO
import com.noproject.common.data.WeightedRandom
import com.noproject.common.domain.model.{Money, Percent}
import io.chrisdavenport.fuuid.FUUID

import scala.math.BigDecimal.RoundingMode
import scala.util.Random

trait RandomValueGenerator {
  def randomBoolean:Boolean                   = Random.nextBoolean()
  def randomOptBoolean:Option[Boolean]        = if (randomBoolean) Some(randomBoolean) else None
  def randomStringLen(len: Int = 10): String  = Random.alphanumeric.take(len).mkString("")
  def randomString: String                    = randomStringLen(10)
  def randomOptString:Option[String]          = randomOptBoolean.map(_ => randomString)
  def randomDouble:Double                     = Random.nextDouble()
  def randomOptDouble:Option[Double]          = randomOptBoolean.map(_ => randomDouble)
  def randomLong:Long                         = Random.nextLong()
  def randomOptLong:Option[Long]              = randomOptBoolean.map(_ => randomLong)
  def randomInt:Int                           = Random.nextInt()
  def randomInt(max:Int):Int                  = Random.nextInt(max)
  def randomIntRange(min: Int, max:Int):Int   = Random.nextInt(max - min) + min
  def randomOptInt:Option[Int]                = randomOptBoolean.map(_ => randomInt)
  def randomOptInt(max: Int):Option[Int]      = randomOptBoolean.map(_ => randomInt(max))
  def randomOptIntRange(min: Int, max: Int):Option[Int] = randomOptBoolean.map(_ => randomIntRange(min, max))
  def randomBigDecimal(scale:Int):BigDecimal  = BigDecimal(Random.nextDouble()).setScale(scale, RoundingMode.HALF_UP)
  def randomBigDecimal2:BigDecimal            = randomBigDecimal(2)
  def randomOptBigDecimal2:Option[BigDecimal] = randomOptBoolean.map(_ => randomBigDecimal2)
  def randomLocalDate:LocalDate               = LocalDate.of(randomInt(2) + 2018, randomInt(12) + 1, randomInt(28) + 1)
  def randomOptDateTime:Option[LocalDate]     = randomOptBoolean.map(_ => randomLocalDate)
  def randomInstant:Instant                   = Instant.ofEpochMilli(Instant.now().plusMillis(randomInt(1000000).toLong).toEpochMilli)
  def randomOptInstant:Option[Instant]        = randomOptBoolean.map(_ => randomInstant)
  def randomOf[A](s:Seq[A]):Seq[A]            = s.filter(_ => randomBoolean)
  def randomOneOf[A](s:Seq[A]):A              = s(Random.nextInt(s.length))
  def randomOneOf[A](s:WeightedRandom[A]):A   = s.getValue
  def randomUUID: FUUID                       = FUUID.randomFUUID[IO].unsafeRunSync()
  def randomStringUUID: String                = randomUUID.toString
  def randomAmount(max: Int): BigDecimal      = BigDecimal(randomInt(max) * 0.01).setScale(2,RoundingMode.HALF_UP)
  def randomMoney(max: Int): Money            = Money(randomAmount(max))
  def randomPercent(max: Int): Percent        = Percent(randomAmount(max))
  def randomOptMoney(max: Int): Option[Money] = randomOptBoolean.map(_ => randomMoney(max))
  def randomOptPct(max: Int): Option[Percent] = randomOptBoolean.map(_ => randomPercent(max))
  def randomLongInRange(from: Long, to: Long): Long             = from + (Random.nextDouble()*(to - from)).toLong
  def randomInstantInRange(from: Instant, to: Instant): Instant = Instant.ofEpochMilli(randomLongInRange(from.toEpochMilli, to.toEpochMilli))
}

object RandomValueGenerator extends RandomValueGenerator