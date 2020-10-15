package com.noproject.common.service.crap

import cats.effect.internals.IOAppPlatform
import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.stream.{RabbitProducer}
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

@Singleton
case class TestTxnProducer @Inject()(rp: RabbitProducer[CashbackTransaction], env: EnvironmentMode) extends DefaultLogging {
  implicit val cs = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)

  def push(ct: CashbackTransaction): IO[Unit] = {
    logger.info(s"Pushed to test queue: $ct")
    if (env == EnvironmentMode.Prod) IO.unit
    else rp.submit(s"${ct.customerName}", List(ct))
  }
}
