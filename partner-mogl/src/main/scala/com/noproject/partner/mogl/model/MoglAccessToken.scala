package com.noproject.partner.mogl.model

case class MoglAccessToken(
  access_token: String
  ,token_type: String
  ,expires_in: Int
  ,scope: String
  )
{
  override def toString: String = access_token
}
