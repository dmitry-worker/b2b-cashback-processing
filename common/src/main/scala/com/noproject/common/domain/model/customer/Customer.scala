package com.noproject.common.domain.model.customer

import cats.effect.IO
import com.noproject.common.security.Hash

case class Customer (
    name:          String // customer alias for real customer
  , apiKey:        String // customer api key
  , hash:          String // customer's hash made from customerName, apiKey and apiSecret
  , role:          Set[AccessRole]
  , webhookUrl:    Option[String]  // required for `push` delivery
  , webhookKey:    Option[String]  // required for `push` delivery
  , active:        Boolean = true  // customer is active or deleted
  , webhookActive: Boolean = true  // required for `push` delivery
)

object CustomerUtil {

  private def buildStr(name: String, key: String, secret: String) = name + "-" + key + "-" + secret

  def calculateHash(name: String, key: String, secret: String): IO[String] = {
    Hash.pass.hash(buildStr(name, key, secret)).map(st => st.toString)
  }

  def checkHash(name: String, key: String, secret: String, hash: String): IO[Boolean] = {
    Hash.pass.check(buildStr(name, key, secret), hash)
  }
}
