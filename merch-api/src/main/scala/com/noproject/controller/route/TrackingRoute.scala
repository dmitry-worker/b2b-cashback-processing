package com.noproject.controller.route

import cats.effect.IO
import com.noproject.common.controller.route.{MonitoredRouting, Routing}
import com.noproject.common.logging.DefaultLogging.{CustomerMarker, OfferMarker, UserMarker}
import com.noproject.common.service.auth.Authenticator
import com.noproject.domain.model.customer.CustomerSession
import com.noproject.service.TrackingService
import javax.inject.{Inject, Named, Singleton}
import org.http4s.Request


@Singleton
class TrackingRoute @Inject()(
  ds: TrackingService
//, @Named("customer")
//  authenticator: Authenticator[CustomerSession]
) extends Routing with MonitoredRouting {

  override val monitoringPrefix = "track"

  val apiPath = "track"

  "Track user" **
    GET / apiPath +?
    (param[String]("offer") &
    param[String]("customer") &
    param[String]("user")) |>> {
    (req: Request[IO], offerId: String, customerName: String, userId: String) =>
      // example of markers usage in log4s
      logger.info("TrackUser", CustomerMarker(customerName), UserMarker(userId), OfferMarker(offerId))
      redeemAndRedirect(ds.getTrackingLink(customerName, userId, offerId))
  }

}
