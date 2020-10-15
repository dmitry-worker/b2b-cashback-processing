package com.noproject.common.controller.dto

import java.time.Instant

import cats.data.NonEmptyList
import com.noproject.common.domain.model.transaction.CashbackTxnStatus
import io.chrisdavenport.fuuid.FUUID

case class TransactionSearchParams(
  ids:      Option[SearchParamsRule[FUUID]] = None
, user:     Option[String] = None
, merchant: Option[String] = None
, network:  Option[String] = None
, beginsAt: Option[Instant] = None
, endsAt:   Option[Instant] = None
, status:   Option[CashbackTxnStatus] = None
, limit:    Option[Int] = None
, offset:   Option[Int] = None
) {

  def withIds(ns: NonEmptyList[FUUID]): TransactionSearchParams = this.copy(ids = Some(SearchParamsRule(true, ns).update(this.ids)))

  def withoutIds(ns: NonEmptyList[FUUID]): TransactionSearchParams = this.copy(ids = Some(SearchParamsRule(false, ns).update(this.ids)))

  def withId(txnid: String): TransactionSearchParams = {
    withIds(NonEmptyList(FUUID.fromStringOpt(txnid).get, Nil))
  }

}

object TransactionSearchParams {
  def apply(): TransactionSearchParams =
    new TransactionSearchParams(None, None, None, None, None)
}

