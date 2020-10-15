package com.noproject.service

import java.time.Clock

import cats.effect._
import cats.implicits._
import com.noproject.common.Executors
import com.noproject.common.cache.SimpleCache
import io.circe.Json
import javax.inject.{Inject, Singleton}
import org.http4s.Uri._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.io._
import io.circe.generic.auto._
import org.http4s.{EntityDecoder, Method, Query, Uri}
import org.http4s.blaze.http.HttpRequest
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.duration._
import org.http4s.circe.jsonOf
import org.http4s.client.Client

import scala.util.{Failure, Success}

@Singleton
class RateService @Inject()(clock: Clock, hc: Client[IO])(implicit t: Timer[IO], cs:ContextShift[IO]) {

  def getRates:IO[Rates] = cache.demand

  private val cache = SimpleCache
    .apply[Rates](300, getLatestRates)
    .unsafeRunTimed(1 seconds)
    .get

  private implicit val decoder: EntityDecoder[IO, Rates] = {
    jsonOf[IO, Rates]
  }

  private val url = Uri
    .unsafeFromString("https://openexchangerates.org")
    .withPath("/api/latest.json")
    .withQueryParam("app_id", "9946692501e547bb985ef1ec79b80cda")


  private def getLatestRates: IO[Rates] = {
    hc.expect[Rates](Method.POST(url))
  }

}
