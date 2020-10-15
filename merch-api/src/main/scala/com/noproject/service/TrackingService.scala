package com.noproject.service

import java.net.URLEncoder
import java.time.Instant

import cats.effect.IO
import com.noproject.common.Exceptions.ObjectNotFoundException
import com.noproject.common.domain.model.customer.{Consumer, PlainTrackingParams}
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import com.noproject.common.domain.service.{ConsumerDataService, MerchantCacheService}
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.logging.DefaultLogging.{CustomerMarker, OfferMarker, DefaultMarkerLogger, UserMarker}
import com.noproject.common.security.Hash
import com.noproject.common.stream.impl.ConsumerQueue
import javax.inject.{Inject, Singleton}

@Singleton
class TrackingService @Inject()(uds: ConsumerDataService, cacheService: MerchantCacheService, queue: ConsumerQueue) extends DefaultLogging {

  private def createDeferred(customerName: String, userId: String): IO[Consumer] = {
    val consumer = Consumer(customerName, userId)
    queue.put(consumer).map(_ => consumer)
  }

  def getTrackingLink(customerName: String, userId: String, offerId: String): IO[String] = {

    def createLink(offer: MerchantOfferRow, user: Consumer, offerId: String): IO[String] = {
      IO.delay {
        if (offer.trackingRule.isEmpty) throw ObjectNotFoundException(Some(s"Offer ${offer.offerId} url is undefined"))
        val trackingHash = URLEncoder.encode(TrackingOps.calculateTrackingHash(user, offerId), "UTF-8")
        val rl = offer.trackingRule.get.replace("{userId}", trackingHash)
        rl.trim
      }
    }

    // example of markers usage in sl4j
    logger.info(s"Retrieving tracking url", CustomerMarker(customerName), UserMarker(userId), OfferMarker(offerId))

    for {
      // TODO: make a stream for deferred user creation to speed up tracking
      // TODO: fs2.Stream[User].groupWithin(5 seconds, 50).map { _.distinctBy(userId) } -> [rabbit ->] DB
      cons <- createDeferred(customerName, userId)
      offr <- cacheService.findOfferRowById(offerId)
      link <- createLink(offr, cons, offerId)
    } yield link
  }

}
