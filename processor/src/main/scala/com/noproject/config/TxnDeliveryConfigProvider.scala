package com.noproject.config

import java.time.Clock

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import com.noproject.common.config.{CachedConfigProvider, ConfigProvider, DBConfigProvider, DefaultFileConfigProvider, FallbackConfigProvider}
import com.noproject.common.domain.dao.ConfigDAO
import com.typesafe.config.Config
import cron4s.{Cron, CronExpr}
import io.circe.{Decoder, DecodingFailure}
import javax.inject.{Inject, Singleton}
import io.circe.generic.auto._

@Singleton
case class TxnDeliveryConfigProvider @Inject()(
  globalConf: Config
, configDAO: ConfigDAO
, clock: Clock
)(
  implicit val T: Timer[IO]
, implicit val C: ContextShift[IO]
)
  extends DBConfigProvider[TxnDeliveryConfig]
    with CachedConfigProvider[TxnDeliveryConfig]
    with FallbackConfigProvider[TxnDeliveryConfig] {

  override protected val configPath: String = "txn.delivery"

  private implicit val cronCodec: Decoder[CronExpr] = Decoder.instance[CronExpr] { cursor =>
    cursor.as[String].flatMap(s =>
      Cron.apply(s).left.map { err => DecodingFailure(err.getLocalizedMessage, cursor.history) }
    )
  }

  override protected val decoder: Decoder[TxnDeliveryConfig] = Decoder[TxnDeliveryConfig]


  override lazy val fallback: ConfigProvider[TxnDeliveryConfig] = {
    new DefaultFileConfigProvider[TxnDeliveryConfig](configPath, globalConf)(decoder)
  }

}