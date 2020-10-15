package com.noproject.partner.mogl

import java.time.Instant

import cats.effect.{ContextShift, IO, Resource}
import com.noproject.common.Executors
import com.noproject.common.config.ConfigProvider
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.logging.DefaultLogging
import com.noproject.partner.mogl.config.MoglConfig
import com.noproject.partner.mogl.model.{MerchantMogl, MoglMerchantRewardType, MoglMerchantScheduleType}
import io.circe.{Decoder, Json}
import org.http4s.client.Client
import org.http4s.circe.jsonOf
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}

/**
  * This class once was part of MoglIntegrationService
  * But then we've decided to separate its functionality for mocking in tests
  */
@Singleton
class MoglHttpClient @Inject()(
  moglCP: ConfigProvider[MoglConfig]
, client:     Client[IO]
, envMode: EnvironmentMode
)(implicit cs: ContextShift[IO]) extends DefaultLogging {


  implicit val reqDec: EntityDecoder[IO, Json] = jsonOf[IO, Json]


  private def httpClient: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](Executors.miscExec)
    .withMaxTotalConnections(5)
    .resource

  private implicit val instantDecoder: Decoder[Instant] = Decoder.instance(jv =>
    jv.as[Long].map { millis => Instant.ofEpochMilli(millis) }
  )

  private implicit val scheduleTypeDecoder: Decoder[MoglMerchantScheduleType] = Decoder.instance(jv =>
    jv.as[String].map { name => MoglMerchantScheduleType.withNameInsensitive(name) }
  )

  private implicit val rewardTypeDecoder: Decoder[MoglMerchantRewardType] = Decoder.instance(jv =>
    jv.as[String].map { name => MoglMerchantRewardType.withNameInsensitive(name) }
  )


  def fetchMoglOffers: IO[fs2.Stream[IO, List[MerchantMogl]]] = {
      for {
        config <- moglCP.getConfig
        result <- IO.delay(fs2.Stream.unfoldEval(1)(fetchMoglOffers0(_, httpClient, config)))
      } yield result
  }

  def fetchMoglOffers0(page: Int, httpClient: Resource[IO, Client[IO]], config: MoglConfig): IO[Option[(List[MerchantMogl], Int)]] = {

    def buildRequest(target: Uri) = Request[IO](
      method = Method.POST
    , uri = target
    )

    // page number must be greater than 0
    if (page <= 0) IO.pure(None) else {

      val url = Uri
        .unsafeFromString(s"https://${config.api.host}")
        .withPath("/api/v2/venues/search")
        .withQueryParam("client_id", config.api.key)
        .withQueryParam("numResults", 100)
        .withQueryParam("page", page)

        client.expect[Json](buildRequest(url)).flatMap { resp: Json =>
          val mercs = resp.hcursor
            .downField("response")
            .downField("results")
            .downField("results")
            .as[List[Json]]
          mercs match {
            case Left(error) =>
              IO.raiseError(new RuntimeException(s"Failed to decode mogl json: ${error}"))
            case Right(jsonEncodedMogls) => IO.delay {
              val endOfStory = jsonEncodedMogls.isEmpty || envMode == EnvironmentMode.Test
              val result = jsonEncodedMogls.map { jo =>
                jo.as[MerchantMogl] match {
                  case Left(error) =>
                    throw new RuntimeException(s"Failed to decode mogl json: ${error}")
                  case Right(mogl) =>
                    mogl.copy(jsonRaw = Some(jo))
                }
              }
              val newPage = if (endOfStory) -1 else page + 1
              Some(result -> newPage)
            }
          }
        }
      }
  }
}
