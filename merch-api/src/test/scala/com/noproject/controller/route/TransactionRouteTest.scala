package com.noproject.controller.route

import cats.effect.IO
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.dao.{CashbackTransactionDAO, DefaultPersistenceTest}
import com.noproject.common.domain.model.transaction.CashbackTransaction
import com.noproject.common.domain.service.CashbackTransactionDataService
import com.noproject.config.AuthConfig
import com.noproject.domain.dao.customer.{CustomerSessionDAO, SessionDAO}
import com.noproject.service.auth.AuthenticatorBypass
import io.circe.Json
import org.http4s.{HttpRoutes, Method, Status, Uri}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.http4s.client.dsl.io._
import org.http4s.util.CaseInsensitiveString
import org.scalamock.scalatest.MockFactory

class TransactionRouteTest extends WordSpec with Matchers with DefaultPersistenceTest with RandomValueGenerator with BeforeAndAfterAll with MockFactory {

  private val clock = TestClock.apply

  private val dao   = new CashbackTransactionDAO(xar)
  private val sdao  = new CustomerSessionDAO(xar, new SessionDAO(xar), new CustomerDAO(xar))
  private val ds        = stub[CashbackTransactionDataService]
  private val customers = "Customer1" :: "Customer2" :: Nil
  (ds.stream _).when(*, *).returns(fs2.Stream.emits(List(genTxn))).anyNumberOfTimes
  (ds.find _).when(*, *).returns(IO.delay(List(genTxn))).anyNumberOfTimes

  var routes: HttpRoutes[IO] = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val ac  = new AuthenticatorBypass
    routes  = new TransactionRoute(ds, ac).toRoutes(identity)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
  }

  "TxnRoute" should {

    "reply streamed csv" in {
      val uri = Uri.unsafeFromString("http://localhost/api/v1/transactions/stream")
      val req = Method.POST("{}", uri).unsafeRunSync()

      val res = routes.run(req).value
      val response = res.unsafeRunSync().get

      response.status shouldEqual Status.Ok
      response.headers.find(_.name == CaseInsensitiveString("Content-Disposition")) shouldBe defined
    }

    "reply json when queried with params" in {
      val uri = Uri.unsafeFromString("http://localhost/api/v1/transactions")
      val req = Method.POST("{}", uri).unsafeRunSync()

      val res = routes.run(req).value
      val response = res.unsafeRunSync().get

      response.status shouldEqual Status.Ok
      println(response.bodyAsText.compile.string.unsafeRunSync())
      succeed
    }

  }

  implicit def genJson:Generator[Json] = new Generator[Json] {
    override def apply: Json = Json.fromFields(
      List("fieldName" -> Json.fromString("fieldValue"))
    )
  }

  private def txnGen: Generator[CashbackTransaction] = Generator[CashbackTransaction]
  private def genTxn: CashbackTransaction = {
    txnGen.apply.copy(
      id = randomUUID
    , customerName = randomOneOf( customers )
    , merchantNetwork = randomOneOf( customers ) + "_network"
    )
  }

}
