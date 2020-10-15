package com.noproject.common.domain.model.transaction

import java.time.Instant

import io.chrisdavenport.fuuid.FUUID
import io.circe.Json

case class TxnChangeLogRow(
  txnId:        FUUID
, customerName: String
, whenCreated:  Instant
, diff:         Json
)
