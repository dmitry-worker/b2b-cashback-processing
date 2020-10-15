package com.noproject.partner.mogl

import java.time.{Clock, Instant}

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.dao.UnknownTransactionDAO
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.{MerchantDataService, MerchantMappingDataService}
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.service.CommonIntegrationService
import com.noproject.common.stream.RabbitProducer
import com.noproject.partner.mogl.model.{MerchantMogl, MoglTxnUpdate}
import com.noproject.service.PartnerTrackingService
import com.typesafe.scalalogging.LazyLogging
import io.prometheus.client.CollectorRegistry
import javax.inject.{Inject, Singleton}
import org.http4s.client.Client


@Singleton
class MoglIntegrationService @Inject()(
  rabbit:     RabbitProducer[CashbackTransaction]
, envMode:    EnvironmentMode
, utdao:      UnknownTransactionDAO
, mds:        MerchantDataService
, ts:         PartnerTrackingService
, mmDS:       MerchantMappingDataService
, moglClient: MoglHttpClient
, cr:         CollectorRegistry
, clock:      Clock
, levelDB:    LevelDBPersistence[CashbackTransaction]
)(implicit val cs: ContextShift[IO], timer: Timer[IO], conc: Concurrent[IO]) extends CommonIntegrationService("mogl", utdao, rabbit, mds, ts, cr, clock, levelDB)(timer, conc) with DefaultLogging {

  // TODO
  //  implement method
  //  retrive user info from mogl
  private[mogl] def submitMoglTxn(src: MoglTxnUpdate): IO[Unit] = ???

  def sync(atTime: Instant, chunk: List[MerchantMogl], mappings: MerchantMappings ): IO[Unit] = {
    if (chunk.isEmpty) IO.unit else {
      val mercs = chunk.map(_.asMerchant(mappings))
      val offers = chunk.map(_.asOffer(mappings, atTime))

      for {
        _ <- updateMerchantRows(mercs)
        _ <- updateOfferRows(offers, atTime)
        _ <- mds.markOffersUpdated(offers.map(_.offerId), atTime)
      } yield ()
    }
  }

  def run: IO[Unit] = {
    val moment = clock.instant()
    val io = for {
      srcStream   <- moglClient.fetchMoglOffers
      mappings    <- mmDS.getMappings
      resStream    = srcStream.evalMap { chunk => sync(moment, chunk, mappings) }
      _           <- resStream.compile.drain
    } yield ()

    io.flatMap { _ =>
      logger.warn(s"Finished mogl synchronization, prepare to delete obsolete offers and update metrics")
      deleteOffers0( moment ) *> updateMetrics0( moment )
    }
  }

}
