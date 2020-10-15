package com.noproject.stress

import cats.effect.{IO, Resource}
import cats.implicits._
import com.google.inject.Guice
import com.noproject.common.ConfigUtil
import com.noproject.common.app.CommonApp
import com.noproject.common.controller.dto.OfferSearchParams
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.customer.ConsumerDAO
import com.noproject.common.domain.model.customer.{AccessRole, Consumer}
import com.noproject.common.domain.service.{CashbackTransactionDataService, CustomerDataService, MerchantDataService, NetworkDataService}
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.stream.DefaultRabbitTest
import com.noproject.partner.azigo.domain.model.AzigoTxn
import com.noproject.partner.button.domain.model.{UsebuttonPayload, UsebuttonTxn}
import com.noproject.partner.coupilia.domain.model.CoupiliaTxn
import com.noproject.stress.config.StressTestConfig
import com.noproject.stress.data.gen.{AzigoGenerator, CoupiliaGenerator, UsebuttonGenerator}
import com.noproject.stress.service.StressRunner
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import io.chrisdavenport.fuuid.FUUID
import io.circe.generic.auto._
import org.http4s.client.Client
import org.http4s.metrics.prometheus.PrometheusExportService
import shapeless.{::, HNil}


object StressTestApp extends CommonApp with DefaultRabbitTest with DefaultLogging {

  val suiteId      = FUUID.randomFUUID[IO].unsafeRunSync
  val customerName = "testCustomer"
  val stressConfig = ConfigUtil.decodeUltimately[StressTestConfig](parentConfig, "stress")

  override protected type Resources = (
     DefaultPersistence
  :: Client[IO]
  :: Fs2Rabbit[IO]
  :: HNil
  )

  override protected def createResources(pes:  PrometheusExportService[IO]): Resource[IO, Resources] = for {
    pres    <- DefaultPersistence.transactor(dbConfig, Some(pes.collectorRegistry))
    http    <- buildHttpClient(16, 5, 3)
    fs2r    <- buildRabbit
  } yield pres :: http :: fs2r :: HNil

  override protected def createServices(res: Resources, pes: PrometheusExportService[IO]): List[IO[_]] = {
    val (pers ::  http :: fs2r :: HNil ) = res

    val mm        = new TestServerModule(this.timer, parentConfig, pers, http)
    val injector  = Guice.createInjector(mm)

    val mds       = injector.getInstance(classOf[MerchantDataService])
    val cds       = injector.getInstance(classOf[CustomerDataService])
    val nds       = injector.getInstance(classOf[NetworkDataService])
    val tds       = injector.getInstance(classOf[CashbackTransactionDataService])
    val uds       = injector.getInstance(classOf[ConsumerDAO])
    val osp       = OfferSearchParams(limit = Some(10), purchaseOnline = Some(true))

    val consumers = (0 until 10).toList.map ( _ => Consumer(customerName, RandomValueGenerator.randomStringUUID) )

    def prepareIO: IO[_] = {
      val availableNetworks = {
        val confNetworks = List(stressConfig.azigo, stressConfig.button, stressConfig.coupilia)
        val allNetworks  = List("azigo", "usebutton", "coupilia")
        (confNetworks zip allNetworks).collect { case (Some(conf), name) => name }
      }

      for {
        _ <- initRabbitBridge("txns", "txns:testCustomer", "testCustomer", fs2r)
        _ <- cds.createIfNotExists(customerName, s"${suiteId}", s"${suiteId}", Set(AccessRole.Customer))
        _ <- nds.updateCustomerNetworks(customerName, availableNetworks)
        _ <- uds.insert(consumers)
      } yield ()
    }

    prepareIO.unsafeRunSync()

    val azigoStressTest = {
      logger.info(s"Running azigo stress test with suiteId: ${suiteId}")
      val azigoOffers = mds.findOffers(osp).unsafeRunSync
      stressConfig.azigo match {
        case Some(ac) =>
          val gen    = new AzigoGenerator(ac, azigoOffers, consumers, suiteId.toString)
          val runner = new StressRunner[AzigoTxn](suiteId.toString, http, gen, ac, tds)
          runner.drainWithIO *> runner.poll
        case _ =>
          ().pure[IO]
      }
    }

    val buttonStressTest = {
      logger.info(s"Running button stress test with suiteId: ${suiteId}")
      val buttonOffers = mds.findOffers(osp).unsafeRunSync
      stressConfig.button match {
        case Some(ac) =>
          val gen    = new UsebuttonGenerator(ac, buttonOffers, consumers, suiteId.toString)
          val runner = new StressRunner[UsebuttonTxn](suiteId.toString, http, gen, ac, tds)
          runner.drainWithIO *> runner.poll
        case _ =>
          ().pure[IO]
      }
    }

    val coupiliaStressTest = {
      logger.info(s"Running coupilia stress test with suiteId: ${suiteId}")
      val coupiliaOffers = mds.findOffers(osp).unsafeRunSync
      stressConfig.coupilia match {
        case Some(ac) =>
          val gen    = new CoupiliaGenerator(ac, coupiliaOffers, consumers, suiteId.toString)
          val runner = new StressRunner[CoupiliaTxn](suiteId.toString, http, gen, ac, tds)
          runner.drainWithIO *> runner.poll
        case _ =>
          ().pure[IO]
      }
    }

    List(coupiliaStressTest)

  }


}
