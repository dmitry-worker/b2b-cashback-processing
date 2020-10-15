package com.noproject.partner.coupilia.route

import java.time._

import cats.effect.IO
import com.noproject.common.codec.json.InstantCodecs
import com.noproject.common.config.ConfigProvider
import com.noproject.common.controller.dto.TxnRefreshInterval
import com.noproject.common.controller.route.S2sRouting
import com.noproject.common.stream.StreamEvent
import com.noproject.partner.coupilia.config.CoupiliaConfig
import com.noproject.partner.coupilia.domain.model.CoupiliaTxn
import com.noproject.partner.coupilia.service.CoupiliaIntegrationService
import fs2.concurrent.Topic
import javax.inject.{Inject, Named, Singleton}
import org.http4s.{AuthedRoutes, QueryParamDecoder}

@Singleton
class InternalRoute @Inject()(
  service:    CoupiliaIntegrationService
, coupiliaCP: ConfigProvider[CoupiliaConfig]
, topic: Topic[IO, StreamEvent[CoupiliaTxn]]
, @Named("s2sUser") user: String
, @Named("s2sPassword") password: String
)(implicit clock: Clock) extends S2sRouting(user, password) with InstantCodecs {
  implicit val instantQPDecoder: QueryParamDecoder[Instant] = QueryParamDecoder[Long].map(s => Instant.ofEpochMilli(s))
  object FromParam extends OptionalQueryParamDecoderMatcher[Instant]("from")
  object ToParam extends OptionalQueryParamDecoderMatcher[Instant]("to")

  override def routes =
    basicAuth(AuthedRoutes.of[String, IO] {
      case GET -> Root / "api" / "internal" / "txn" / "refresh" :? FromParam(from) +& ToParam(to) as user =>
        val interval = TxnRefreshInterval(from, to)
        val io = for {
          cfg  <- coupiliaCP.getConfig
          txns <- service.getTxns(
            interval.from.atZone(ZoneOffset.UTC).toLocalDate,
            interval.to.atZone(ZoneOffset.UTC).toLocalDate,
            cfg)
          res  <- service.submitCoupiliaTxns(txns)
        } yield res
        io.flatMap(_ => Ok(""))
    })
}
