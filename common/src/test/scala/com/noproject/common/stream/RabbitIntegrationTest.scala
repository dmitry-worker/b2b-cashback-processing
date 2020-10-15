package com.noproject.common.stream

import java.time.Instant
import java.time.temporal.ChronoField

import cats.implicits._
import cats.effect.{CancelToken, ContextShift, IO, Resource, Timer}
import com.noproject.common.TestIO
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.model.transaction.{CashbackTransaction, CashbackTxnStatus}
import com.noproject.common.stream.RabbitOps.TransactionCallback
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model
import dev.profunktor.fs2rabbit.model.AckResult.Ack
import dev.profunktor.fs2rabbit.model.{AckResult, DeliveryTag}
import io.circe.Json
import org.scalatest.{Matchers, WordSpec}
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.Money
import fs2.Pipe

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.math.BigDecimal.RoundingMode

class RabbitIntegrationTest extends WordSpec with DefaultRabbitTest with RandomValueGenerator with Matchers {

  val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"
  , port     = 5672
  , user     = Some("rabbit")
  , password = Some("rabbit")
  )

  implicit val timer: Timer[IO]         = IO.timer(ExecutionContext.global)
  implicit val shift: ContextShift[IO]  = IO.contextShift(ExecutionContext.global)

  private val fs2rconf  = RabbitConfig.buildConfig(conf)
  private lazy val fs2r = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()

  "RabbitServiceApp" should {

    "initialize a pub, sub and transmit a message successfully using immediate prod" in {
      var result = Set[CashbackTransaction]()
      val txns = (0 until 10).map(_ => genTxn).toList
      val io = immediateRabbitBridge[CashbackTransaction]("txns", "test-q", "test-rk", fs2r).use {
        case (pub, rcons) =>

          rcons.drainWith( env => result ++= env.contents ).start.unsafeRunSync

          // first we post half of transactions
          val first = pub.submit("test-rk", txns.take(5))

          // then the others - one by one
          val last = txns.drop(5).map { txn => pub.submit("test-rk", List(txn)) }


          first >> last.sequence >> timer.sleep(3000 millis)
      }

      val completeIO = (io.runCancelable(_ => IO.unit).toIO <* timer.sleep(3200 millis)).map(ct => ct.unsafeRunSync())
      completeIO.unsafeRunSync

      result shouldEqual txns.toSet
    }

    "initialize a new sub" in {
      var messageCount: Int = 0

      val producerResourceIO = immediateRabbitBridge[CashbackTransaction]("txns", "test-q", "test-rk", fs2r).allocated
      val ((prod, cons), clear) = producerResourceIO.unsafeRunSync()

      cons.drainWith { env =>
        messageCount += 1
      }.start.unsafeRunSync()

      val txn1 = genTxn
      val submitIO = prod.submit("test-rk", txn1 :: Nil) *> timer.sleep(1 seconds)
      submitIO.unsafeRunSync()

      messageCount shouldEqual 1
    }
  }


  def genTxn: CashbackTransaction = {
    val now = Instant.now.`with`(ChronoField.NANO_OF_SECOND, 0L).minusSeconds(randomInt(60*60*24))
    val amount = randomInt(1000)
    CashbackTransaction(
      id = randomUUID
    , userId = randomString
    , customerName = randomString
    , reference = randomString
    , merchantName = "7-eleven"
    , merchantNetwork = "azigo"
    , description = None
    , whenCreated = now
    , whenUpdated = now
    , whenClaimed = None
    , whenSettled = None
    , whenPosted = None
    , purchaseAmount = Money(amount)
    , purchaseDate = now
    , purchaseCurrency = "USD"
    , cashbackBaseUSD = Money(BigDecimal(amount * 0.10))
    , cashbackTotalUSD = Money(BigDecimal(amount * 0.10))
    , cashbackUserUSD = Money(BigDecimal(amount * 0.03))
    , cashbackOwnUSD = Money(BigDecimal(amount * 0.07))
    , status = CashbackTxnStatus.Pending
    , parentTxn = None
    , payoutId = None
    , failedReason = None
    , rawTxn = Json.fromFields(Nil)
    , offerId = randomOptString
    , offerTimestamp = Some(now)
    )
  }

}
