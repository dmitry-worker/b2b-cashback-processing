package com.noproject.partner.mogl

import java.time.Instant
import java.util.concurrent.Executors

import cats.effect.{Blocker, IO}
import com.noproject.common.config.{ConfigProvider, EnvironmentMode, FailFastConfigProvider, OffersConfig}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.domain.LevelDBPersistence
import com.noproject.common.domain.dao.{DefaultPersistenceTest, UnknownTransactionDAO}
import com.noproject.common.domain.dao.merchant._
import com.noproject.common.domain.dao.partner.NetworkDAO
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.partner.Network
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.{MerchantDataService, MerchantMappingDataServiceImpl}
import com.noproject.common.stream.{RabbitConfig, SimpleRabbitConfig, DefaultRabbitTest}
import com.noproject.partner.mogl.config.{MoglApiConfig, MoglConfig}
import com.noproject.partner.mogl.model._
import com.noproject.service.PartnerTrackingService
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import io.circe.Json
import io.prometheus.client.CollectorRegistry
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.{JavaNetClientBuilder, UnexpectedStatus}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.ExecutionContext


class MoglIntegrationServiceTest extends WordSpec
  with DefaultPersistenceTest
  with DefaultRabbitTest
  with RandomValueGenerator
  with Matchers
  with BeforeAndAfterAll
  with MockFactory {

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  private val clock = TestClock.apply

  private val mnDAO = new MerchantNameMappingDAO(xar)
  private val utDAO = new UnknownTransactionDAO(xar)
  private val mcDAO = new MerchantCategoryMappingDAO(xar)
  private val mDAO  = new MerchantDAO(xar)
  private val nDAO  = new NetworkDAO(xar)
  private val moDAO = new MerchantOfferDAO(xar, clock)
  private val modDAO = new MerchantOfferDiffDAO(xar)

  private val mmDS  = new MerchantMappingDataServiceImpl(mnDAO, mcDAO)
  private val mDS  = new MerchantDataService(mDAO, moDAO, modDAO, xar, StaticOffersConfigProvider, clock )
  private val ts   = mock[PartnerTrackingService]

  private val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"
    , port     = 5672
    , user     = Some("rabbit")
    , password = Some("rabbit")
  )

  private val allowedMerchants = (1 to 10).map { i => s"Merchant$i"}
  private val newMoglMercs = (0 until 195).map(_ => genMerc(allowedMerchants)).toList
  private val newMercs = newMoglMercs.foldLeft(Set[String]())( (s, el) => s + el.name )

  private val fs2rconf  = RabbitConfig.buildConfig(conf)
  private val fs2r      = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()
  private val exName    = "offers"
  private val exchange  = ExchangeName(exName)
  private val rkName    = "mogl"
  private val routing   = RoutingKey(rkName)
  private val moment0   = Instant.now()
  private val moment1   = moment0.plusSeconds(1)
  private val qName     = "offers"
  private val queue     = QueueName(qName)

  private val ((pub, sub), rclear)  = immediateRabbitBridge[CashbackTransaction](exName, qName, rkName, fs2r).allocated.unsafeRunSync()
  private val (leveldb, ldbclear)   = LevelDBPersistence.getInstance[CashbackTransaction]("example.db").allocated.unsafeRunSync()
  private val (cli, cclear)         = BlazeClientBuilder[IO](ExecutionContext.global).withMaxTotalConnections(1).resource.allocated.unsafeRunSync()
//  private val service               =


  object StaticCP extends ConfigProvider[MoglConfig] with FailFastConfigProvider [MoglConfig] {
    override protected def load: IO[MoglConfig] = IO.pure {
      MoglConfig(MoglApiConfig(
        "38e9b6ef-725a-41e6-b60b-aee0e0af4429" //client_id
        , "secret" //secret
        , "test.mogl.com"
      ))
    }
  }

  object StaticUnauthorizedCP extends ConfigProvider[MoglConfig] with FailFastConfigProvider [MoglConfig] {
    override protected def load: IO[MoglConfig] = IO.pure {
      MoglConfig(MoglApiConfig(
        "UnknownClientId" //client_id
        , "UnknownSecret" //secret
        , "test.mogl.com"
      ))
    }
  }

  private val existingMoglUserId = "789546" //this exists on the Mogl Test system.  However, they can purge it at any time
  private val nonexistingMoglUserId = "a123" //they use #'s only, so this can never exist

  override protected def beforeAll(): Unit = {
    // should exist because of FK
    super.beforeAll()
    val io = for {
      _ <- moDAO.deleteAll()
      _ <- mDAO.deleteAll()
      _ <- nDAO.deleteAll()
      _ <- nDAO.insert(Network("mogl", None) :: Nil)
    } yield ()
    io.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    val io = for {
      _ <- moDAO.deleteAll()
      _ <- mDAO.deleteAll()
      _ <- nDAO.deleteAll()
      _ <- rclear
      _ <- ldbclear
    } yield ()
    io.unsafeRunSync()
    super.afterAll()
  }



  "MoglIntegrationServiceTest" should {

    "create offers successfully" in {
      val moglClient = stub[MoglHttpClient]
      val portions = newMoglMercs.grouped(50).toList
      (moglClient.fetchMoglOffers _).when().returns(IO.pure(fs2.Stream.emits(portions))).anyNumberOfTimes

      val service = new MoglIntegrationService(pub, EnvironmentMode.Test, utDAO, mDS, ts, mmDS, moglClient, new CollectorRegistry, clock, leveldb)
      service.run.unsafeRunSync()

      val persMercs = mDAO.findAll.unsafeRunSync().map(_.merchantName).toSet
      persMercs shouldEqual newMercs
      val persOffers = moDAO.findAll.unsafeRunSync()
      IO.pure(persOffers.size shouldEqual 195)
    }

    "update offers successfully" in {
      val first       = newMoglMercs.head
      val second      = newMoglMercs.tail.head // we'll miss that one
      val lasts       = newMoglMercs.tail.tail

      val altered     = first.copy(latitude = 10.00, longitude = 20.00) :: lasts
      val portions    = altered.grouped(50).toList
      val alteredSet  = altered.foldLeft(Set[String]())((s, el) => s + el.name)

      val moglClient = stub[MoglHttpClient]
      (moglClient.fetchMoglOffers _).when().returns(IO.pure(fs2.Stream.emits(portions))).anyNumberOfTimes

      val service = new MoglIntegrationService(pub, EnvironmentMode.Test, utDAO, mDS, ts, mmDS, moglClient, new CollectorRegistry, clock, leveldb)
      service.run.unsafeRunSync()

      val persMercs = mDAO.findAll.unsafeRunSync().map(_.merchantName).toSet
      persMercs shouldEqual newMercs
      val persOffers = moDAO.findAll.unsafeRunSync()
      IO.pure(persOffers.size shouldEqual 195)
    }

    "fetch user info for existing user" in {
      val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
      val httpClient: client.Client[IO] = {
        JavaNetClientBuilder[IO](Blocker.liftExecutionContext( blockingEC ) ).create
      }

      val config = StaticCP.getConfig.unsafeRunSync()
      val access = MoglAccess( httpClient, config )
      val mogly = access.runMogl {
        access.getUserInfo( existingMoglUserId )
      }

      val r : MoglUserInfoResponse = mogly.unsafeRunSync()

      //todo: Mogl currently only returns anonymous data.  Need to confirm with their engineers
      //how to get actual user data.  In essence, we can confirm a user EXISTS, but nothing
      //about the user
      r.response.user.firstname shouldEqual Some("Anonymous")
    }

    "fetch user info non-existing user" in {
      val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
      val httpClient: client.Client[IO] = JavaNetClientBuilder[IO](Blocker.liftExecutionContext( blockingEC ) ).create

      val config = StaticCP.getConfig.unsafeRunSync()
      val access = MoglAccess( httpClient, config )
      val mogly = access.runMogl {
        access.getUserInfo( nonexistingMoglUserId )
      }

      val caught = intercept[UnexpectedStatus] {
        mogly.unsafeRunSync()
      }
      caught.status shouldEqual Status.NotFound
    }

    "mogl unauthorized request - bad clientId" in {
      val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
      val httpClient: client.Client[IO] = JavaNetClientBuilder[IO](Blocker.liftExecutionContext( blockingEC ) ).create

      val config = StaticUnauthorizedCP.getConfig.unsafeRunSync()
      val access = MoglAccess( httpClient, config )
      val mogly = access.runMogl {
        access.getUserInfo( existingMoglUserId )
      }

      val caught = intercept[UnexpectedStatus] {
        mogly.unsafeRunSync()
      }

      caught.status shouldEqual Status.Unauthorized
    }

  }



  def genMerc(allowedMerchants: Seq[String]): MerchantMogl = {

    implicit val jsonGen: Generator[Json] = new Generator[Json] { override def apply: Json = Json.Null }
    implicit val dtGen = Generator[MoglMerchantDetails]
    implicit val rtGen = Generator[MoglMerchantOffer]
    implicit val maGen = Generator[MoglAddress]

    Generator[MerchantMogl].apply.copy(
      name               = randomOneOf(allowedMerchants)
      , categories         = randomOf(List("catA", "catB", "catC")).toList
      , buzz               = randomOf(List("buzzA", "buzzB", "buzzC")).toList
      , acceptedCards      = randomOf(List("cardA", "cardB", "cardC")).toList
    )

  }

}