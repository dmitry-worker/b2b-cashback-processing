package com.noproject.controller.dto.customer

import com.noproject.common.domain.model.customer.Customer

case class AdminCustomerManageRequest(name: String, key: String, secret: String, role: Set[String]) {
  def toResp = AdminCustomerManageResponse(name, key, role)
}

case class AdminCustomerManageResponse(name: String, key: String, role: Set[String])

object AdminCustomerManageResponse {
  def apply(c: Customer): AdminCustomerManageResponse = {
    new AdminCustomerManageResponse(c.name, c.apiKey, c.role.map(_.entryName))
  }
}