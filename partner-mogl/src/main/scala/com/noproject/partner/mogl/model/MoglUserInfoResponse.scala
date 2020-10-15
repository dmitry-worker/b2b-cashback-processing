package com.noproject.partner.mogl.model

case class MoglUserInfoResponse(
      meta:              Map[String, Int]
   ,  response:          MoglUserResponse
 )

case class MoglUserResponse (
      user: MoglUserInfo
      )
