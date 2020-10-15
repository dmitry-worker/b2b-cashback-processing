package com.noproject.controller.route

import java.time.{Instant, ZoneId, ZonedDateTime}

import cats.effect.IO
import com.noproject.common.service.auth.Authenticator
import com.noproject.controller.dto.dashboard.{DashboardOffersStatsResponse, DashboardStatsRequest, DashboardTransactionsStatsResponse}
import com.noproject.domain.model.customer.CustomerSession
import com.noproject.service.statistics.StatisticsService
import io.circe.generic.auto._
import javax.inject.{Inject, Named, Singleton}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityEncoder, Request}

@Singleton
class DashboardRoute @Inject()(
  service: StatisticsService
, @Named("customer")
  authenticator: Authenticator[CustomerSession]
) extends AuthenticatedRouting(authenticator) {
  import com.noproject.common.codec.json.ElementaryCodecs._

  implicit val oEncoder: EntityEncoder[IO, DashboardOffersStatsResponse] = jsonEncoderOf[IO, DashboardOffersStatsResponse]
  implicit val tEncoder: EntityEncoder[IO, DashboardTransactionsStatsResponse] = jsonEncoderOf[IO, DashboardTransactionsStatsResponse]

  val apiPath = baseApiPath / "v1" / "dashboard"

  val defaultIntervalBeginning = Instant.from(ZonedDateTime.of(2019, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")))

  private val defaultLimit = 10


  "Retrieve offers stats" **
  GET / apiPath / "offers" >>> Auth.auth |>> { session: CustomerSession =>
    logger.info(s"GET retrieve offers KPIs for user ${session.customerName}")
    redeemAndSuccess {
      service.collectOffers
    }
  }

   "Retrieve txns stats" **
   POST / apiPath / "txns" >>> Auth.auth ^ jsonOf[IO, DashboardStatsRequest] |>> {
     (session: CustomerSession, dsr: DashboardStatsRequest) => {
       logger.info(s"POST retrieve txns KPIs with request ${dsr} for user ${session.customerName}")
       val dsrWithDefaults = dsr.copy(
         tags = dsr.tags.map(_.map(_.toLowerCase)),
         beginsAt = dsr.beginsAt.orElse(Some(defaultIntervalBeginning)),
         endsAt = dsr.endsAt.orElse(Some(Instant.now))
       )
       redeemAndSuccess {
         service.collectTxns(dsrWithDefaults, session.customerName)
       }
     }
  }

}
