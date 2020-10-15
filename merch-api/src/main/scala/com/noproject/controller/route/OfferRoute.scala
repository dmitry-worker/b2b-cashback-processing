package com.noproject.controller.route

import cats.effect.IO
import com.noproject.common.controller.dto.OfferSearchParams
import com.noproject.common.controller.route.MonitoredRouting
import com.noproject.common.domain.model.merchant.Merchant
import com.noproject.common.domain.service.MerchantDataService
import com.noproject.common.service.auth.Authenticator
import com.noproject.domain.model.customer.CustomerSession
import io.circe.generic.auto._
import javax.inject.{Inject, Named, Singleton}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder, Request}

@Singleton
class OfferRoute @Inject()(
  ds: MerchantDataService
, @Named("customer")
  authenticator: Authenticator[CustomerSession]
) extends AuthenticatedRouting(authenticator) with MonitoredRouting {

  import com.noproject.common.codec.json.ElementaryCodecs._

  override def monitoringPrefix: String = "offers"

  implicit val mercEncoder: EntityEncoder[IO, Merchant] = jsonEncoderOf[IO, Merchant]
  implicit val mercSeqEncoder: EntityEncoder[IO, List[Merchant]] = jsonEncoderOf[IO, List[Merchant]]
  implicit val ospDecode: EntityDecoder[IO, OfferSearchParams] = jsonOf[IO, OfferSearchParams]

  val offersApiPath = baseApiPath / "v1" / "offers"

  private val defaultLimit = 10

  private def normalize(osp: OfferSearchParams): OfferSearchParams = {
    osp.copy(
      limit = osp.limit.orElse(Some(defaultLimit)),
      networks = osp.networks.map(spr =>
        spr.copy(values = spr.values.map(_.toLowerCase))
      ),
      tags = osp.tags.map(spr =>
        spr.copy(values = spr.values.map(_.toLowerCase))
      )
    )
  }


   "Search offers" **
   POST / offersApiPath >>> Auth.auth |>> { (req: Request[IO], session: CustomerSession) =>
     redeemAndSuccess {
       for {
         osp <- req.as[OfferSearchParams]
         _    = logger.info(s"POST search offers with request ${osp} for user ${session.customerName}")
         res <- ds.findMerchantOffers(normalize(osp), session.customerName)
       } yield res
     }
  }

  "Get offer by identifier" **
  GET / offersApiPath / pathVar[String]("offerId") >>> Auth.auth |>> { (offerId: String, session: CustomerSession) =>
    logger.info(s"GET offer by id ${offerId} for user ${session.customerName}")
    redeemAndSuccess {
      ds.findOfferById(offerId, session.customerName)
    }
  }

  "Get last new offers" **
  GET / offersApiPath / "new" +? param[Option[Int]]("limit") >>> Auth.auth |>> { (limit: Option[Int], session: CustomerSession) =>
    logger.info(s"GET new offers for user ${session.customerName}")
    redeemAndSuccess {
      ds.findNewOffers(limit.getOrElse(defaultLimit), session.customerName)
    }
  }

  "Get last featured offers" **
  GET / offersApiPath / "featured" +? param[Option[Int]]("limit") >>> Auth.auth |>> { (limit: Option[Int], session:CustomerSession) =>
    logger.info(s"GET featured offers for user ${session.customerName}")
    redeemAndSuccess {
      ds.findFeaturedOffers(limit.getOrElse(defaultLimit), session.customerName)
    }
  }

}
