package com.noproject.partner.button.route

import cats.effect.{IO, Resource}
import com.noproject.common.Executors
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.partner.button.config.UsebuttonConfig
import com.noproject.partner.button.domain.model.{UsebuttonCents, UsebuttonCodecs, UsebuttonTxn}
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.{Decoder, Json}
import org.http4s.circe.jsonEncoderOf
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.{EntityEncoder, Header, Headers, MediaType, Request, Response, Status, Uri}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, Matchers, WordSpec}
import tsec.common._
import tsec.mac.jca.HMACSHA256
import com.noproject.common.security.Hash.hmacsha256withSecret

@DoNotDiscover
class UsebuttonWebhookIntegrationTest extends WordSpec
  with DefaultPersistenceTest
  with UsebuttonCodecs
  with MockFactory
  with RandomValueGenerator
  with BeforeAndAfterAll
  with Matchers {

  val cfg = UsebuttonConfig(
    webhookSecret = "secret"
  , apiKey = "sk-7DKqQDn7EAWoMVHO5KZzXv"
  , apiSecret = ""
  , expirationMinutes = 60
  , url = "https://api.usebutton.com"
  , organizationId = "org-xxx"
  , accountId = "acc-xxx"
  )

  private val WEBHOOK_TEST_URL = "http://localhost:8502"
  private val XBUTTON_AUTH_HEADER = "X-Button-Signature"
  private val HMAC_KEY = HMACSHA256.unsafeBuildKey(cfg.webhookSecret.utf8Bytes)

  lazy val httpClient: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](Executors.miscExec)
    .withMaxTotalConnections(5)
    .resource


  object StaticConfigProvider extends ConfigProvider[UsebuttonConfig] with FailFastConfigProvider[UsebuttonConfig] {
    override protected def load: IO[UsebuttonConfig] = IO.pure(cfg)
  }

  implicit val encoder: EntityEncoder[IO, Json] = jsonEncoderOf[IO, Json]

  "UsebuttonRouteIntegrationTest" should {


    "parse request" in {
      val res = Decoder[UsebuttonTxn].decodeJson(parse(UsebuttonWebhookRequests.someJson).right.get)
      println(res)
      res.isRight shouldBe true
    }

    "calculate valid mac" in {
      val mac = hmacsha256withSecret.hex(HMAC_KEY, UsebuttonWebhookRequests.correctJsonRequest)
      val req = Request[IO](
        method = POST
        , uri = Uri.unsafeFromString(WEBHOOK_TEST_URL).withPath("/webhook/v1/usebutton")
        , headers = Headers.of(Header(XBUTTON_AUTH_HEADER, mac.get), `Content-Type`(MediaType.application.json))
      ).withEntity(UsebuttonWebhookRequests.correctJsonRequest)

      val str = req.bodyAsText.compile.string.unsafeRunSync()
      str shouldEqual UsebuttonWebhookRequests.correctJsonRequest
      val newMac = hmacsha256withSecret.hex(HMAC_KEY, str)
      mac shouldEqual newMac
    }


    "apply signed request" in {
      val mac = hmacsha256withSecret.hex(HMAC_KEY, UsebuttonWebhookRequests.correctJsonRequest)

      val req = Request[IO](
        method = POST
      , uri = Uri.unsafeFromString(WEBHOOK_TEST_URL).withPath("/webhook/v1/usebutton")
      , headers = Headers.of(Header(XBUTTON_AUTH_HEADER, mac.get), `Content-Type`(MediaType.application.json))
      ).withEntity(UsebuttonWebhookRequests.correctJsonRequest)

      val res = httpClient.use { client =>
        client.fetch(req) { res: Response[IO] =>
          IO.pure(res.status)
        }
      }.unsafeRunSync()

      res shouldBe Status.Ok
    }

    "apply unsigned request" in {
      val req = Request[IO](
        method = POST
        , uri = Uri.unsafeFromString(WEBHOOK_TEST_URL).withPath("/webhook/v1/usebutton")
        , headers = Headers.of(`Content-Type`(MediaType.application.json))
      ).withEntity(UsebuttonWebhookRequests.correctJsonRequest)

      val res = httpClient.use { client =>
        client.fetch(req) { res: Response[IO] =>
          IO.pure(res.status)
        }
      }.unsafeRunSync()

      res shouldBe Status.Unauthorized
    }

    "apply fake request" in {
      val req = Request[IO](
        method = POST
        , uri = Uri.unsafeFromString(WEBHOOK_TEST_URL).withPath("/webhook/v1/usebutton")
        , headers = Headers.of(Header(XBUTTON_AUTH_HEADER, "ololo"), `Content-Type`(MediaType.application.json))
      ).withEntity(UsebuttonWebhookRequests.correctJsonRequest)

      val res = httpClient.use { client =>
        client.fetch(req) { res: Response[IO] =>
          IO.pure(res.status)
        }
      }.unsafeRunSync()

      res shouldBe Status.Unauthorized
    }

    "apply bad request" in {
      val mac = hmacsha256withSecret.hex(HMAC_KEY, UsebuttonWebhookRequests.correctJsonRequest)

      val req = Request[IO](
        method = POST
        , uri = Uri.unsafeFromString(WEBHOOK_TEST_URL).withPath("/webhook/v1/usebutton")
        , headers = Headers.of(Header(XBUTTON_AUTH_HEADER, mac.get), `Content-Type`(MediaType.application.json))
      ).withEntity(UsebuttonWebhookRequests.incorrectJsonRequest)

      val res = httpClient.use { client =>
        client.fetch(req) { res: Response[IO] =>
          IO.pure(res.status)
        }
      }.unsafeRunSync()

      res shouldBe Status.Unauthorized
    }
  }
}
