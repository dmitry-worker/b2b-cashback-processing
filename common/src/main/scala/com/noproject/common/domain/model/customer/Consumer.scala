package com.noproject.common.domain.model.customer

import com.noproject.common.security.Hash

case class Consumer (
  customerName: String
, userId: String
, hash: String
)

object Consumer {

  val Unknown = "Unknown"

  def apply(customerName: String, userId: String): Consumer = {
    val hash: String = Hash.md5.hex(customerName + "::" + userId)
    Consumer(customerName, userId, hash)
  }

  val empty = Consumer(Unknown, Unknown)

}
