package com.noproject.partner.azigo.domain.model

case class AzigoTxnUpdate(
  id:           Long
, newOne:       AzigoTxn
, existing:     Option[AzigoTxn]
) {

  require(id == newOne.transactionId && existing.forall(_.transactionId == id))

}
