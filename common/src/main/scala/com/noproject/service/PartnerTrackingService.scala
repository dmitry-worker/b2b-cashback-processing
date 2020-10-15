package com.noproject.service

import java.net.URLEncoder
import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.Exceptions.ObjectNotFoundException
import com.noproject.common.domain.model.customer.{Consumer, PlainTrackingParams, WrappedTrackingParams}
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import com.noproject.common.domain.service.{ConsumerDataService, MerchantCacheService}
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.logging.DefaultLogging.{CustomerMarker, OfferMarker, DefaultMarkerLogger, UserMarker}
import com.noproject.common.security.Hash
import javax.inject.{Inject, Singleton}

import scala.util.{Failure, Success, Try}

@Singleton
class PartnerTrackingService @Inject()(uds: ConsumerDataService) extends DefaultLogging {

  private def plainToWrapped(u: Consumer, p: PlainTrackingParams): WrappedTrackingParams = {
    val t  = Try (Instant.ofEpochMilli(p.time.toLong) ).toOption
    val o  = if (p.offer.isEmpty) None else Some(p.offer)
    WrappedTrackingParams(u, o, t)
  }

  def decodeTrackingHash(trackingCode: String): IO[WrappedTrackingParams] = {
    for {
      hash <- IO.delay(Hash.base64.decode(trackingCode))
      ptp   = PlainTrackingParams(hash)
      user <- uds.getByHash(ptp.user)
      tp    = plainToWrapped(user, ptp)
    } yield tp
  }


  /**
    * Input:
    *   hash1,
    *   hash2,
    *   hash3
    * Output:
    *   hash1 -> Success(WTP),
    *   hash2 -> Failure(NoSuchElementException(Consumer XXX does not exist),
    *   hash3 -> Failure(IllegalArgumentException("Can't parse that hash: hash3"))
    * @param hashes
    * @return
    */
  def decodeHashesBatchAndGetTrackingParams(hashes: List[String]): IO[Map[String, Try[WrappedTrackingParams]]] = {

    val decoded: IO[Map[String, Try[PlainTrackingParams]]] = IO.delay {
      hashes.map( el => el -> Hash.base64.tryDecode(el).map(PlainTrackingParams.apply) ).toMap
    }

    for {
      res <- decoded
      decodedSuccessfully = res.collect { case (k, Success(v)) => v -> k          }
      decodedFailures     = res.collect { case (k, Failure(_)) => k -> Failure(new IllegalArgumentException(s"Can't parse that hash: ${k}")) }
      decodedParams       = decodedSuccessfully.keySet.toList
      wrappedParams      <- if (decodedParams.isEmpty) IO.pure(Map[String, Try[WrappedTrackingParams]]())
                            else {
                              val distinctConsumers = decodedParams.map(_.user).distinct
                              // TODO: we should carefully analyze consumers or maybe all params
                              // TODO: this would might help us to detect fraud
                              val nel = NonEmptyList.fromListUnsafe(distinctConsumers)
                              uds.getByHashBatch(nel).map { consumerList =>
                                val consumers = consumerList.map { c => c.hash -> c }.toMap
                                decodedSuccessfully.map { case (params, hash) =>
                                  consumers.get(params.user) match {
                                    case Some(consumer) => hash -> Success( plainToWrapped(consumer, params) )
                                    case _              => hash -> Failure( new NoSuchElementException(s"Consumer ${params.user} not found."))
                                  }
                                }
                              }
                            }
    } yield wrappedParams ++ decodedFailures

  }

  def extractTrackingParams(trackingHash: Option[String]): IO[WrappedTrackingParams] = {
    trackingHash match {
      case None         => IO.pure(WrappedTrackingParams.empty)
      case Some(custId) => decodeTrackingHash(custId)
    }
  }

}
