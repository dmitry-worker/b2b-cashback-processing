package com.noproject.partner.azigo.route

import java.time.{Clock, Instant}

import cats.effect.IO
import cats.implicits._
import com.noproject.common.codec.json.InstantCodecs
import com.noproject.common.controller.dto.TxnRefreshInterval
import com.noproject.common.controller.route.S2sRouting
import com.noproject.common.stream.{StreamData, StreamEvent}
import com.noproject.partner.azigo.AzigoIntegrationService
import com.noproject.partner.azigo.domain.model.AzigoTxn
import fs2.concurrent.Topic
import javax.inject.{Inject, Named, Singleton}
import org.http4s.{AuthedRoutes, QueryParamDecoder}

@Singleton
class InternalRoute @Inject()(
  service: AzigoIntegrationService
, @Named("s2sUser") user: String
, @Named("s2sPassword") password: String
, txnTopic: Topic[IO, StreamEvent[AzigoTxn]]
)(implicit clock: Clock) extends S2sRouting(user, password) with InstantCodecs {

  implicit val instantQPDecoder: QueryParamDecoder[Instant] = QueryParamDecoder[Long].map(s => Instant.ofEpochMilli(s))
  object FromParam extends OptionalQueryParamDecoderMatcher[Instant]("from")
  object ToParam extends OptionalQueryParamDecoderMatcher[Instant]("to")

  override def routes =
    basicAuth(AuthedRoutes.of[String, IO] {
      case GET -> Root / "api" / "internal" / "txn" / "refresh" :? FromParam(from) +& ToParam(to) as user => {
        val interval = TxnRefreshInterval(from, to)
        val io = for {
          txns  <- service.getAzigoTxns(interval.from, interval.to)
          res   <- txns.map( t => txnTopic.publish1(StreamData(t)) ).sequence
        } yield res
        io.void.flatMap(Ok(_))
      }
    })
}
