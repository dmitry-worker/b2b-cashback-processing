package com.noproject.common.domain.model.transaction

import java.time.Instant

import com.noproject.common.domain.codec.DomainCodecs.txnRespEnc
import io.chrisdavenport.fuuid.FUUID
import io.circe.Json

case class CashbackTransactionResponse(
  id:                 FUUID
, userId:             String
, customerName:       String
, reference:          String
, merchantName:       String
, merchantNetwork:    String
, description:        Option[String]
, whenCreated:        Instant
, whenUpdated:        Instant
, whenClaimed:        Option[Instant]
, whenSettled:        Option[Instant]
, whenPosted:         Option[Instant]
, purchaseDate:       Instant
, purchaseAmount:     BigDecimal
, purchaseCurrency:   String
, cashbackBaseUSD:    BigDecimal
, cashbackTotalUSD:   BigDecimal
, cashbackUserUSD:    BigDecimal
, cashbackOwnUSD:     BigDecimal
, status:             CashbackTxnStatus
, parentTxn:          Option[FUUID] = None
, diff:               Option[Json] = None
) {

  lazy val asJson = txnRespEnc.apply(this)

}

object CashbackTransactionResponse {

  def apply(ct: CashbackTransaction, diffOpt: Option[Json]): CashbackTransactionResponse =
    new CashbackTransactionResponse(
      id = ct.id,
      userId = ct.userId,
      customerName = ct.customerName,
      reference = ct.reference,
      merchantName = ct.merchantName,
      merchantNetwork = ct.merchantNetwork,
      description = ct.description,
      whenCreated = ct.whenCreated,
      whenUpdated = ct.whenUpdated,
      whenClaimed = ct.whenClaimed,
      whenSettled = ct.whenSettled,
      whenPosted = ct.whenPosted,
      purchaseDate = ct.purchaseDate,
      purchaseAmount = ct.purchaseAmount.amount,
      purchaseCurrency = ct.purchaseCurrency,
      cashbackBaseUSD = ct.cashbackBaseUSD.amount,
      cashbackTotalUSD = ct.cashbackTotalUSD.amount,
      cashbackUserUSD = ct.cashbackUserUSD.amount,
      cashbackOwnUSD = ct.cashbackOwnUSD.amount,
      status = ct.status,
      parentTxn = ct.parentTxn,
      diff = diffOpt
    )

  def apply(ct: CashbackTransaction): CashbackTransactionResponse = apply(ct, None)

}