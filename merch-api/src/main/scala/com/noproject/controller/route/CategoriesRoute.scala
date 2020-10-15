package com.noproject.controller.route

import cats.effect.IO
import com.noproject.common.domain.service.MerchantDataService
import com.noproject.common.service.auth.Authenticator
import com.noproject.domain.model.customer.CustomerSession
import javax.inject.{Inject, Named, Singleton}
import org.http4s.circe.jsonEncoderOf
import org.http4s.{EntityEncoder, Request}

@Singleton
class CategoriesRoute @Inject()(
  ds: MerchantDataService
, @Named("customer")
  authenticator: Authenticator[CustomerSession]
) extends AuthenticatedRouting(authenticator) {

  implicit val resEncoder: EntityEncoder[IO, List[String]] = jsonEncoderOf[IO, List[String]]

  val apiPath = baseApiPath / "v1" / "categories"

  "Get categories for customer" **
  GET / apiPath >>> Auth.auth |>> { session: CustomerSession =>
    logger.info(s"GET categories for user ${session.customerName}")
    redeemAndSuccess {
      ds.getCategories(session.customerName)
    }
  }
}
