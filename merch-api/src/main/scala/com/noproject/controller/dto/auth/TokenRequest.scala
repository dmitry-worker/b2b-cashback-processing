package com.noproject.controller.dto.auth

import cats.effect.IO

case class TokenRequest (
  customerName: String
, apiKey: String
, apiSecret: String
) {


//  def asCustomer: IO[Customer] = {
//    CustomerUtil.calculateHash(customerName, apiKey, apiSecret).map { hash =>
//      Customer(0, customerName, apiKey, hash)
//    }
//  }
}