package com.noproject.partner.button.route

import java.time.{Clock, Instant}

import cats.effect.{ContextShift, IO}
import com.noproject.common.codec.json.InstantCodecs
import com.noproject.common.controller.dto.TxnRefreshInterval
import com.noproject.common.controller.route.S2sRouting
import com.noproject.common.stream.{StreamData, StreamEvent}
import com.noproject.partner.button.domain.model.{UsebuttonPayload, UsebuttonTxn}
import com.noproject.partner.button.{UseButtonIntegrationService, UseButtonUpdaterService}
import fs2.concurrent.Topic
import javax.inject.{Inject, Named, Singleton}
import org.http4s.{AuthedRoutes, QueryParamDecoder}

@Singleton
class InternalRoute @Inject()(
  txnTopic: Topic[IO, StreamEvent[UsebuttonPayload]]
, updater: UseButtonUpdaterService
, @Named("s2sUser") user: String
, @Named("s2sPassword") password: String
)(implicit clock: Clock, cs: ContextShift[IO]) extends S2sRouting(user, password) with InstantCodecs {

  implicit val instantQPDecoder: QueryParamDecoder[Instant] = QueryParamDecoder[Long].map(s => Instant.ofEpochMilli(s))
  object FromParam extends OptionalQueryParamDecoderMatcher[Instant]("from")
  object ToParam extends OptionalQueryParamDecoderMatcher[Instant]("to")

  override def routes =
    basicAuth(AuthedRoutes.of[String, IO] {
      case GET -> Root / "api" / "internal" / "txn" / "refresh" :? FromParam(from) +& ToParam(to) as user => {
        val interval = TxnRefreshInterval(from, to)
        updater.isBusy.flatMap {
          case true  => Locked()
          case false => updater.getAndUpdateUsebuttonTxns(interval.from, interval.to).start.flatMap ( _ => Accepted() )
        }
      }
    })
}
