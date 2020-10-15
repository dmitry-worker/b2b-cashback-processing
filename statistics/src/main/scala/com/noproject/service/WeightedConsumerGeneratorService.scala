package com.noproject.service

import java.time.Instant

import cats.effect.IO
import cats.implicits._
import cats.data.NonEmptyList
import com.noproject.common.controller.dto.{OfferSearchParams, SearchParamsRule}
import com.noproject.common.domain.service.{CashbackTransactionDataService, ConsumerDataService, MerchantDataService}
import com.noproject.data.gen.WeightedConsumerGenerator
import com.typesafe.scalalogging.LazyLogging
import javax.inject.{Inject, Singleton}

@Singleton
class WeightedConsumerGeneratorService @Inject()(
  custDS: ConsumerDataService
, mercDS: MerchantDataService
, txnsDS: CashbackTransactionDataService
) extends LazyLogging {

  def run(totalQty: Int): IO[Unit] = {

    val merchantIO = {
      val netParams = SearchParamsRule(true, NonEmptyList("azigo", Nil))
      val params = OfferSearchParams(networks = Some(netParams))
      mercDS.findMerchantOffers(params, None)
    }

    for {
      mercs  <- merchantIO
      gen     = new WeightedConsumerGenerator(mercs)
      result <- (0 until totalQty).map( _ => run1(gen) ).toList.sequence
    } yield ()

  }


  private def run1(generator: WeightedConsumerGenerator) = {
    val (cust, profile, txns) = generator.genNext("mastercard")
    for {
      _ <- custDS.insertConsumer(cust.customerName, cust.userId, Some(profile))
      _ <- txnsDS.batchProcess(NonEmptyList.fromListUnsafe(txns), Instant.now)
    } yield ()
  }

}
