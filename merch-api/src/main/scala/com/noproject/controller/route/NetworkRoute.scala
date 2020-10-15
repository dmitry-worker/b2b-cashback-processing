package com.noproject.controller.route

import cats.effect.IO
import com.noproject.common.domain.model.partner.CustomerNetwork
import com.noproject.common.domain.service.NetworkDataService
import com.noproject.common.service.auth.Authenticator
import com.noproject.controller.dto.customer.CustomerNetworksUpdateRequest
import com.noproject.domain.model.customer.CustomerSession
import io.circe.generic.auto._
import javax.inject.{Inject, Named, Singleton}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder, Request}

@Singleton
class NetworkRoute @Inject()(
  ds: NetworkDataService
, @Named("customer")
  authenticator: Authenticator[CustomerSession]) extends AuthenticatedRouting(authenticator) {

  implicit val reqDecoder: EntityDecoder[IO, List[CustomerNetworksUpdateRequest]] = jsonOf[IO, List[CustomerNetworksUpdateRequest]]
  implicit val dtoEncoder: EntityEncoder[IO, List[CustomerNetwork]] = jsonEncoderOf[IO, List[CustomerNetwork]]

  val apiPath = baseApiPath / "v1" / "networks"

  "Get networks for customer" **
    GET / apiPath >>> Auth.auth |>> { session: CustomerSession =>
      logger.info(s"GET networks for user ${session.customerName}")
      val io = session.customerName match {
        case Some(id) => ds.getNetworksForCustomer(id)
        case None     => IO(Nil)
      }
      redeemAndSuccess(io)
    }

  "Update networks for customer" **
    POST / apiPath >>> Auth.auth ^ jsonOf[IO, CustomerNetworksUpdateRequest] |>> { (session: CustomerSession, dto: CustomerNetworksUpdateRequest) =>
      logger.info(s"POST update networks for user ${session.customerName}")
      val io = (session.customerName match {
        case Some(id) => ds.updateCustomerNetworks(id, dto.networks.map(_.toLowerCase))
        case None => IO.unit
      }).map(_ => "")
      redeemAndSuccess(io)
    }

  "Disable one network for customer" **
    DELETE / apiPath / pathVar[String]("network") >>> Auth.auth |>> { (network: String, session: CustomerSession) =>
      logger.info(s"POST update networks for user ${session.customerName}")
      val io = (session.customerName match {
        case Some(id) => ds.disableNetwork(id, network.toLowerCase)
        case None     => IO.unit
      }).map(_ => "")
      redeemAndSuccess(io)
    }

  "Enable one network for customer" **
    PUT / apiPath / pathVar[String]("network") >>> Auth.auth |>> {
    (network: String, session: CustomerSession) => {
      logger.info(s"POST update networks for user ${session.customerName}")
      val io = (session.customerName match {
        case Some(id) => ds.enableNetwork(id, network.toLowerCase)
        case None => IO.unit
      }).map(_ => "")
      redeemAndSuccess(io)
    }
  }
}
