package com.noproject.common.security

import cats.effect.Sync
import io.circe.{Decoder, Encoder}
import cats.syntax.all._
import tsec.jwt._
import tsec.jws.mac._
import tsec.mac.jca._
import tsec.common._
import io.circe.syntax._

import scala.concurrent.duration._

// TODO: maybe become a part of an authenticator?
class JWT(
  protected val key: String
, protected val expirationMinutes: Long
) {

  lazy val hmKey: MacSigningKey[HMACSHA512] = HMACSHA512.unsafeBuildKey(key.utf8Bytes)

  val customFieldKey = "data"

  def buildToString[F[_]: Sync, A](j: A)(implicit e: Encoder[A]): F[String] = {
    build(j).map(_.toEncodedString)
  }

  def validate[F[_]: Sync, A: Decoder](token: String): F[A] = {
    for {
      result <- JWTMac.verifyAndParse[F, HMACSHA512](token, hmKey)
      data   <- result.body.getCustomF[F,A](customFieldKey)//.asF[F, A]
    } yield data
  }

  def build[F[_]: Sync, A](j: A)(implicit e: Encoder[A]): F[JWTMac[HMACSHA512]] = {
    val exp = Some(expirationMinutes minutes)
    val dto = (customFieldKey -> j.asJson) :: Nil
    for {
      cls <- JWTClaims.withDuration[F](expiration = exp, customFields = dto)
      jwt <- JWTMac.build[F, HMACSHA512](cls, hmKey)
    } yield jwt
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[JWT]

  override def equals(other: Any): Boolean = other match {
    case that: JWT =>
      (that canEqual this) &&
        key == that.key &&
        expirationMinutes == that.expirationMinutes
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(key, expirationMinutes)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

}