package com.noproject.partner.button.domain.model

case class UsebuttonTxnUpdate(
  meta   : UsebuttonMetaUpdate
, objects: List[UsebuttonPayload]
)
