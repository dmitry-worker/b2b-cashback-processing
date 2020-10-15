package com.noproject.common.security

import cats.effect.IO
import org.apache.commons.codec.binary.Base64
import tsec.common._
import tsec.hashing.CryptoHash
import tsec.hashing.jca._
import tsec.mac.jca.{HMACSHA256, MacSigningKey}
import tsec.passwordhashers._
import tsec.passwordhashers.jca._

import scala.util.Try

object Hash {


  object md5 {
    def bytes(text: String): CryptoHash[MD5] = text.utf8Bytes.hash[MD5]
    def hex(text: String): String = { bytes(text).map("%02x".format(_)).mkString }
  }


  object sha1 {
    def bytes(text: String):CryptoHash[SHA1] = text.utf8Bytes.hash[SHA1]
    def hex(text: String): String = bytes(text).map("%02x".format(_)).mkString
  }

  object sha256 {
    def bytes(text: String):CryptoHash[SHA256] = text.utf8Bytes.hash[SHA256]
    def hex(text: String): String = bytes(text).map("%02x".format(_)).mkString
  }

  object hmacsha256withSecret {
    def key(secret: String): MacSigningKey[HMACSHA256] = HMACSHA256.unsafeBuildKey(secret.utf8Bytes)
    def hex(secret: MacSigningKey[HMACSHA256], body: String): Option[String] =
      HMACSHA256.sign(body.utf8Bytes, secret).map(_.map("%02x".format(_)).mkString).toOption
    def hex(secret: String, body: String): Option[String] = hex(key(secret), body)
  }

  object pass {
    def hash(text: String): IO[PasswordHash[HardenedSCrypt]] = HardenedSCrypt.hashpw[IO](text)
    def check(text: String, hash: String):IO[Boolean] = HardenedSCrypt.checkpwBool[IO](text, PasswordHash[HardenedSCrypt](hash))
  }

  object base64 {
    def encode(text: String): String = Base64.encodeBase64URLSafeString(text.utf8Bytes)
    def decode(text: String): String = Base64.decodeBase64(text.utf8Bytes).toUtf8String
    def tryDecode(text: String): Try[String] = Try(decode(text))
  }

}


