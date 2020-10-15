package com.noproject.controller.route.admin

import cats._
import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.noproject.common.controller.route.MonitoredRouting
import com.noproject.common.domain.model.customer.AccessRole
import com.noproject.common.domain.service.CustomerDataService
import com.noproject.common.service.auth.Authenticator
import com.noproject.controller.dto.customer.{AdminCustomerManageRequest, AdminCustomerManageResponse}
import com.noproject.controller.route.AuthenticatedRouting
import com.noproject.domain.model.customer.CustomerSession
import com.noproject.domain.service.customer.SessionDataService
import io.circe.generic.auto._
import javax.inject.{Inject, Named, Singleton}
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}

@Singleton
class AdminCustomerRoute @Inject()(
  ds: CustomerDataService
, @Named("admin")
  authenticator: Authenticator[CustomerSession]
) extends AdminRouting(authenticator) with MonitoredRouting {

  override val monitoringPrefix = "admin_customer"

  implicit val custEncoder: EntityEncoder[IO, AdminCustomerManageRequest] = jsonEncoderOf[IO, AdminCustomerManageRequest]
  implicit val custDecoder: EntityEncoder[IO, AdminCustomerManageResponse] = jsonEncoderOf[IO, AdminCustomerManageResponse]
  implicit val custSeqDecoder: EntityEncoder[IO, List[AdminCustomerManageResponse]] = jsonEncoderOf[IO, List[AdminCustomerManageResponse]]

  val accessRolesDecoder: EntityDecoder[IO, Set[AccessRole]] =
    jsonOf[IO, List[String]].flatMapR( decodeAccessRoles[List] ).map( _.to[Set] )

  def decodeAccessRoles[A[_] : Traverse](as : A[String] ) : DecodeResult[ IO, A[AccessRole]] =
    as.map( decodeAccessRole ).sequence

  def decodeAccessRole( str : String ) : DecodeResult[IO, AccessRole] =
    EitherT.fromEither( AccessRole.withNameOption( str )
      .toRight( InvalidMessageBodyFailure( s"Invalid role $str" ) ) )

  val customerApiPath = baseApiPath / "v1" / "customer"

   "Insert new customer" **
   POST / customerApiPath >>> Auth.auth ^ jsonOf[IO, AdminCustomerManageRequest] |>> { (session: CustomerSession, cmr: AdminCustomerManageRequest) =>
     logger.info(s"POST insert new customer ${cmr.name} by admin ${session.customerName}")
     redeemAndSuccess {
       ds.create(cmr.name, cmr.key, cmr.secret, cmr.role.map(AccessRole.withName)).map(_ => cmr.toResp)
     }
  }

  "List customers" **
  GET / customerApiPath  >>> Auth.auth |>> { session: CustomerSession =>
    logger.info(s"GET list customers by admin ${session.customerName}")
    redeemAndSuccess {
      ds.findAll.map(_.map(AdminCustomerManageResponse(_)))
    }
  }

  "Get customer" **
  GET / customerApiPath / pathVar[String]("apiKey") >>> Auth.auth |>> { (apiKey: String, session: CustomerSession) =>
    logger.info(s"GET customer ${apiKey} by admin ${session.customerName}")
    redeemAndSuccess {
      ds.getByKey(apiKey).map(AdminCustomerManageResponse(_))
    }
  }

  "Delete customer" **
  DELETE / customerApiPath / pathVar[String]("apiKey") >>> Auth.auth |>> { (apiKey: String, session: CustomerSession) =>
    logger.info(s"DELETE customer ${apiKey} by admin ${session.customerName}")
    ds.delete(apiKey).map(_ => "")
  }

  "Update customer roles" **
  PUT / customerApiPath / pathVar[String]( "apiKey" ) / "role" >>> Auth.auth ^ accessRolesDecoder |>> {
    (apiKey: String, session: CustomerSession, role : Set[AccessRole] ) =>
      logger.info( s"UPDATE customer ${apiKey} by admin ${session.customerName}")
      // TODO Redirect to direct customer api URL instead of returning the entire object
      //      (broken semantics)
      ds.updateRoles( apiKey, role )
        .map( AdminCustomerManageResponse( _ ) )
  }
}
