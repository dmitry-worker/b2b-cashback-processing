package com.noproject.common.domain.dao

import cats.implicits._
import cats.effect.IO
import com.noproject.common.controller.dto.TransactionSearchParams
import com.noproject.common.domain.DefaultPersistence
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import fs2.Stream
import io.circe.Json
import java.time.Instant

import cats.data.NonEmptyList
import com.noproject.common.domain.model.transaction.{CashbackTransaction, TxnKey}
import javax.inject.{Inject, Singleton}
import shapeless.{HList, HNil}


@Singleton
class CashbackTransactionDAO @Inject()(sp: DefaultPersistence) extends TransactionDAO(sp, "cashback_transaction")