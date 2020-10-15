package com.noproject.partner.button

import java.time.{Clock, Instant}

import cats.effect.{IO, Resource, Timer}
import com.noproject.common.Executors
import com.noproject.common.codec.json.InstantCodecs
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider}
import com.noproject.common.data.gen.{RandomValueGenerator, TestClock}
import com.noproject.common.domain.dao.{DefaultPersistenceTest, UnknownTransactionDAO}
import com.noproject.common.domain.model.customer.WrappedTrackingParams
import com.noproject.common.domain.model.eventlog.{EventLogItem, EventLogObjectType}
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.{MerchantDataService, MerchantMappingDataService, MerchantMappingDataServiceImpl}
import com.noproject.common.stream._
import com.noproject.partner.button.config.UsebuttonConfig
import com.noproject.partner.button.domain.dao.UsebuttonMerchantDAO
import com.noproject.partner.button.domain.model._
import com.noproject.service.PartnerTrackingService
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.QueueName
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.prometheus.client.CollectorRegistry
import org.http4s.circe.jsonEncoderOf
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import org.http4s.{EntityEncoder, HttpRoutes}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.duration._
import com.noproject.common.domain.codec.DomainCodecs._
import cats.implicits._
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings

import scala.concurrent.ExecutionContext


class UsebuttonUpdaterServiceTest extends WordSpec
  with DefaultPersistenceTest
  with DefaultRabbitTest
  with UsebuttonCodecs
  with InstantCodecs
  with MockFactory
  with RandomValueGenerator
  with BeforeAndAfterAll
  with Matchers {

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)


  val cfg = UsebuttonConfig(
    webhookSecret = "secret"
  , apiKey = "sk-3SDRrPgCTuk8dkHI2uI0Fg"
  , apiSecret = ""
  , expirationMinutes = 60
  , url = "http://localhost:8888"
  , organizationId = "org-xxx"
  , accountId = "acc-xxx"
  )

  object StaticConfigProvider extends ConfigProvider[UsebuttonConfig] with FailFastConfigProvider[UsebuttonConfig] {
    override protected def load: IO[UsebuttonConfig] = IO.pure(cfg)
  }

  private val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"
    , port     = 5672
    , user     = Some("rabbit")
    , password = Some("rabbit")
  )
  private val fs2rconf  = RabbitConfig.buildConfig(conf)
  private val fs2r      = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()

  private val clock = TestClock.apply
  private val ts = stub[PartnerTrackingService]
//  (ts.extractTrackingParams _).when(*).returns(IO.pure(WrappedTrackingParams.empty)).anyNumberOfTimes()

  private val ubmDAO = stub[UsebuttonMerchantDAO]
  private val utDAO = stub[UnknownTransactionDAO]
  private val mDS = stub[MerchantDataService]
  private val mmds = stub[MerchantMappingDataServiceImpl]


  private val exName    = "test-txns"
  private val rkName    = "button-updater"
  private val qName     = s"$exName:$rkName"
  private val queue     = QueueName(qName)

  private val eexName    = "test-events"
  private val erkName    = EventLogObjectType.UsebuttonTxn.entryName
  private val eqName     = s"$eexName:$erkName"
  private val equeue     = QueueName(eqName)


  private val ((pub: RabbitProducer[CashbackTransaction], sub: RabbitConsuming[CashbackTransaction]), rclear) =
    immediateRabbitBridge[CashbackTransaction](exName, qName, rkName, fs2r)
      .allocated
      .unsafeRunSync()

  private val ((epub: RabbitProducer[EventLogItem], esub: RabbitConsuming[EventLogItem]), erclear) =
    immediateRabbitBridge[EventLogItem](eexName, eqName, erkName, fs2r)
      .allocated
      .unsafeRunSync()

  private val (cli, cclear) = BlazeClientBuilder[IO](Executors.miscExec)
    .withMaxTotalConnections(1)
    .resource
    .allocated
    .unsafeRunSync()

  private var destroyServer: IO[Unit] = _

  sealed case class UsebuttonTxnUpdateWithErrors(meta: UsebuttonMetaUpdate, objects: List[Json])
  sealed case class UsebuttonTxnUpdateError(bad_object: String)

  private val intService = mock[UseButtonIntegrationService]
  (intService submitUsebuttonTxns _) expects(*) onCall { arg: List[UsebuttonPayload] =>
    val now   = Instant.now()
    val txns  = arg.map(_.asCashbackTransaction(WrappedTrackingParams.empty, now, MerchantMappings.empty))
    if (txns.nonEmpty) pub.submit(s"$rkName", txns)
    else IO.unit
  } anyNumberOfTimes

  private val service = new UseButtonUpdaterService(StaticConfigProvider, ts, intService, epub, cli, clock)

  override def beforeAll(): Unit = {
    super.beforeAll()

    buildServer.allocated.map {
      case (_, free) =>
        destroyServer = free
    }.unsafeRunSync()
  }

  override def afterAll(): Unit = {
    destroyServer.unsafeRunSync()
    super.afterAll()
  }

  "UsebuttonUpdaterServiceTest" should {

    "parse txn updates" in {
      val (objs, errs) =
        List(chunk1Json, chunk2Json, chunk3Json)
          .map(Decoder[UsebuttonTxnUpdateWithErrors].decodeJson(_).right.get)
          .map(_.objects.map(Decoder[UsebuttonPayload].decodeJson).partition(_.isRight))
          .fold((List.empty, List.empty))((o1, o2) => (o1._1 ++ o2._1, o1._2 ++ o2._2))

      val txnList: List[UsebuttonPayload] = objs.map(_.right.get)

      service.getAndUpdateUsebuttonTxns(clock.instant().minusSeconds(60 * 60 * 24), clock.instant()).unsafeRunSync()

      var txnResult = List[CashbackTransaction]()
      var errResult = List[EventLogItem]()
      val iotxn = sub.drainWith(env => txnResult ++= env.contents).start >> timer.sleep(2 seconds)
      val ioerr = esub.drainWith(env => errResult ++= env.contents).start >> timer.sleep(2 seconds)

      val completeTxnIO = (iotxn.runCancelable(_ => IO.unit).toIO <* timer.sleep(2.5 seconds)).map(ct => ct.unsafeRunSync())
      val completeErrIO = (ioerr.runCancelable(_ => IO.unit).toIO <* timer.sleep(2.5 seconds)).map(ct => ct.unsafeRunSync())
      completeTxnIO.unsafeRunSync
      completeErrIO.unsafeRunSync

      txnResult.size shouldBe 4
      txnResult.map(_.reference).toSet shouldEqual txnList.map(_.id).toSet
      txnResult.map { item =>
        item.rawTxn.isNull shouldBe false
      }

      errResult.size shouldBe 3
      errResult.map {
        item => Decoder[UsebuttonTxnUpdateError].decodeJson(item.rawObject.get).right.get.bad_object
      }.toSet shouldEqual Set("0","1","2")
    }
}

  val chunk1Json =
    parse("""
      |{
      |  "meta": {
      |    "status": "ok",
      |    "next": "http://localhost:8888/next",
      |    "previous": null
      |  },
      |  "objects": [
      |    {
      |      "publisher_customer_id": "1111-3333-4444-999999999999",
      |      "publisher_organization": "org-YYY",
      |      "publisher_organization_name": "Publisher Company Name",
      |      "commerce_organization": "org-XXX",
      |      "commerce_organization_name": "Brand Company Name",
      |      "button_id": "static",
      |      "account_id": "acc-XXX",
      |      "btn_ref": "srctok-XXX",
      |      "pub_ref": "Publisher Reference String",
      |      "button_order_id": "btnorder-XXX",
      |      "order_id": null,
      |      "order_total": 6000,
      |      "order_currency": "USD",
      |      "order_line_items": [
      |        {
      |          "identifier": "sku-1234",
      |          "total": 6000,
      |          "amount": 2000,
      |          "quantity": 3,
      |          "publisher_commission": 600,
      |          "sku": "sku-1234",
      |          "gtin": "00400000000001",
      |          "category": "Clothes",
      |          "subcategory1": "Kids",
      |          "description": "T-shirts",
      |          "attributes": {
      |            "size": "M"
      |          }
      |        }
      |      ],
      |      "order_click_channel": "app",
      |      "order_purchased_date": null,
      |      "category": "new-user-order",
      |      "id": "0",
      |      "created_date": "2019-01-01T19:49:17Z",
      |      "modified_date": "2019-01-01T19:49:17Z",
      |      "validated_date": null,
      |      "attribution_date": "2019-01-01T19:49:17Z",
      |      "amount": 600,
      |      "currency": "USD",
      |      "status": "pending",
      |      "advertising_id": null
      |    }, {
      |      "bad_object": "0"
      |    }
      |  ]
      |}
    """.stripMargin).right.get

  val chunk2Json =
    parse("""
      |{
      |  "meta": {
      |    "status": "ok",
      |    "next": "http://localhost:8888/final",
      |    "previous": null
      |  },
      |  "objects": [
      |    {
      |      "publisher_customer_id": "1111-3333-4444-999999999999",
      |      "publisher_organization": "org-YYY",
      |      "publisher_organization_name": "Publisher Company Name",
      |      "commerce_organization": "org-XXX",
      |      "commerce_organization_name": "Brand Company Name",
      |      "button_id": "static",
      |      "account_id": "acc-XXX",
      |      "btn_ref": "srctok-XXX",
      |      "pub_ref": "Publisher Reference String",
      |      "button_order_id": "btnorder-XXX",
      |      "order_id": null,
      |      "order_total": 6000,
      |      "order_currency": "USD",
      |      "order_line_items": [
      |        {
      |          "identifier": "sku-1234",
      |          "total": 6000,
      |          "amount": 2000,
      |          "quantity": 3,
      |          "publisher_commission": 600,
      |          "sku": "sku-1234",
      |          "gtin": "00400000000001",
      |          "category": "Clothes",
      |          "subcategory1": "Kids",
      |          "description": "T-shirts",
      |          "attributes": {
      |            "size": "M"
      |          }
      |        }
      |      ],
      |      "order_click_channel": "app",
      |      "order_purchased_date": null,
      |      "category": "new-user-order",
      |      "id": "1",
      |      "created_date": "2019-01-01T19:49:17Z",
      |      "modified_date": "2019-01-01T19:49:17Z",
      |      "validated_date": null,
      |      "attribution_date": "2019-01-01T19:49:17Z",
      |      "amount": 600,
      |      "currency": "USD",
      |      "status": "pending",
      |      "advertising_id": null
      |    },
      |    {
      |      "publisher_customer_id": "1111-3333-4444-999999999999",
      |      "publisher_organization": "org-YYY",
      |      "publisher_organization_name": "Publisher Company Name",
      |      "commerce_organization": "org-XXX",
      |      "commerce_organization_name": "Brand Company Name",
      |      "button_id": "static",
      |      "account_id": "acc-XXX",
      |      "btn_ref": "srctok-XXX",
      |      "pub_ref": "Publisher Reference String",
      |      "button_order_id": "btnorder-XXX",
      |      "order_id": null,
      |      "order_total": 6000,
      |      "order_currency": "USD",
      |      "order_line_items": [
      |        {
      |          "identifier": "sku-1234",
      |          "total": 6000,
      |          "amount": 2000,
      |          "quantity": 3,
      |          "publisher_commission": 600,
      |          "sku": "sku-1234",
      |          "gtin": "00400000000001",
      |          "category": "Clothes",
      |          "subcategory1": "Kids",
      |          "description": "T-shirts",
      |          "attributes": {
      |            "size": "M"
      |          }
      |        }
      |      ],
      |      "order_click_channel": "app",
      |      "order_purchased_date": null,
      |      "category": "new-user-order",
      |      "id": "2",
      |      "created_date": "2019-01-01T19:49:17Z",
      |      "modified_date": "2019-01-01T19:49:17Z",
      |      "validated_date": null,
      |      "attribution_date": "2019-01-01T19:49:17Z",
      |      "amount": 600,
      |      "currency": "USD",
      |      "status": "pending",
      |      "advertising_id": null
      |    }
      |  ]
      |}
    """.stripMargin).right.get

  val chunk3Json =
    parse("""
      |{
      |  "meta": {
      |    "status": "ok",
      |    "next": null,
      |    "previous": null
      |  },
      |  "objects": [
      |    {
      |      "publisher_customer_id": "1111-3333-4444-999999999999",
      |      "publisher_organization": "org-YYY",
      |      "publisher_organization_name": "Publisher Company Name",
      |      "commerce_organization": "org-XXX",
      |      "commerce_organization_name": "Brand Company Name",
      |      "button_id": "static",
      |      "account_id": "acc-XXX",
      |      "btn_ref": "srctok-XXX",
      |      "pub_ref": "Publisher Reference String",
      |      "button_order_id": "btnorder-XXX",
      |      "order_id": null,
      |      "order_total": 6000,
      |      "order_currency": "USD",
      |      "order_line_items": [
      |        {
      |          "identifier": "sku-1234",
      |          "total": 6000,
      |          "amount": 2000,
      |          "quantity": 3,
      |          "publisher_commission": 600,
      |          "sku": "sku-1234",
      |          "gtin": "00400000000001",
      |          "category": "Clothes",
      |          "subcategory1": "Kids",
      |          "description": "T-shirts",
      |          "attributes": {
      |            "size": "M"
      |          }
      |        }
      |      ],
      |      "order_click_channel": "app",
      |      "order_purchased_date": null,
      |      "category": "new-user-order",
      |      "id": "3",
      |      "created_date": "2019-01-01T19:49:17Z",
      |      "modified_date": "2019-01-01T19:49:17Z",
      |      "validated_date": null,
      |      "attribution_date": "2019-01-01T19:49:17Z",
      |      "amount": 600,
      |      "currency": "USD",
      |      "status": "pending",
      |      "advertising_id": null
      |    }, {
      |      "bad_object": "1"
      |    }, {
      |      "bad_object": "2"
      |    }
      |  ]
      |}
    """.stripMargin).right.get


  private def buildServer: Resource[IO, Server[IO]] = {
    implicit val encoder: EntityEncoder[IO, Json] = jsonEncoderOf[IO, Json]

    val routes = HttpRoutes.of[IO] {
      case GET -> Root / "v1" / "affiliation" / "accounts" / "acc-xxx" / "transactions" =>
        Ok(chunk1Json)
      case GET -> Root / "next" =>
        Ok(chunk2Json)
      case GET -> Root / "final" =>
        Ok(chunk3Json)
      case s =>
        println(s)
        NotFound()
    }
    val router = Router("" -> routes).orNotFound
    BlazeServerBuilder[IO].bindHttp(8888, "0.0.0.0")
      .withHttpApp(router)
      .withNio2(true)
      .resource
  }

}
