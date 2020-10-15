package com.noproject.controller.route.admin.crap

import java.time.Instant

import cats.effect.IO
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.route.Routing
import com.noproject.service.crap.CrapService
import javax.inject.{Inject, Named, Singleton}
import org.http4s.Request

@Singleton
class CrapRoute @Inject()(
  ds: CrapService
, envMode: EnvironmentMode
) extends Routing {

  private val genApiPath = baseApiPath / "v1" / "crap" / "generate"

  GET / genApiPath / "transactions" / pathVar[Int]("count") +?
    param[Option[String]]("customer")  &
    param[Option[String]]("network") &
    param[Option[Instant]]("from") &
    param[Option[Instant]]("to") |>>
    { (count: Int, customer: Option[String], network: Option[String], from: Option[Instant], to: Option[Instant]) =>
      ds.genTxns(count, customer, network.map(_.toLowerCase), from, to).map(_ => Ok("txns generated"))
  }

  DELETE / genApiPath / "transactions" |>> { _: Request[IO] =>
    ds.deleteTxns().map(_ => Ok("txns deleted"))
  }

  GET / genApiPath / "merchants" / pathVar[Int]("count") |>> { count: Int =>
    ds.genMerchants(count).map(_ => Ok("merchants generated"))
  }

  DELETE / genApiPath / "merchants" |>> { _: Request[IO] =>
    ds.deleteMerchants().map(_ => "merchants deleted")
  }

  GET / genApiPath / "offers" / pathVar[Int]("count") +? param[Option[String]]("network") |>> {
    (count: Int, network: Option[String]) =>
    ds.genOffers(count, network.map(_.toLowerCase())).map(_ => Ok("offers generated"))
  }

  DELETE / genApiPath / "offers" |>> { _: Request[IO] =>
    ds.deleteOffers().map(_ => Ok("offers deleted"))
  }
}
