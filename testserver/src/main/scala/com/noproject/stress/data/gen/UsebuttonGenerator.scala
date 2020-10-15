package com.noproject.stress.data.gen

import java.time.Instant

import cats.effect.IO
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.model.customer.Consumer
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import com.noproject.partner.button.domain.model.{UsebuttonCents, UsebuttonOrderItem, UsebuttonPayload, UsebuttonTxn}
import com.noproject.service.TrackingOps
import com.noproject.stress.config.StressTestPartnerConfig
import fs2.Stream
import io.circe.Json


class UsebuttonGenerator(
  stpc:       StressTestPartnerConfig
, offers:     List[MerchantOfferRow]
, consumers:  List[Consumer]
, suiteId:    String
) extends StreamingGenerator[UsebuttonTxn] with RandomValueGenerator {
  require( offers.nonEmpty )

  val customer = s"testCustomer"
  val (_, totalCount, totalAmount) = StressTestPartnerConfig.unapply(stpc).get
  val TOTAL_CONSUMERS = totalCount / 10
  val TOTAL_AMOUNT    = totalCount * 100D

  def genStream: Stream[IO, UsebuttonTxn] = {

    implicit val genJson:Generator[Option[Json]] = new Generator[Option[Json]] {
      override def apply: Option[Json] = None
    }

    implicit val instantGen: Generator[Instant] = new Generator[Instant] {
      override def apply: Instant = {
        randomInstantInRange(Instant.now.minusSeconds(86400), Instant.now)
      }
    }

    implicit val centsGen: Generator[UsebuttonCents] = new Generator[UsebuttonCents] {
      override def apply: UsebuttonCents = {
        UsebuttonCents(100L)
      }
    }

    implicit val itemsGen: Generator[List[UsebuttonOrderItem]] = new Generator[List[UsebuttonOrderItem]] {
      override def apply: List[UsebuttonOrderItem] = List(
        UsebuttonOrderItem(
          identifier = randomStringUUID
        , amount     = centsGen.apply
        )
      )
    }

    implicit val pgen = Generator[UsebuttonPayload]

    def generatePayload(amt: Double): UsebuttonPayload = {
      val offer     = randomOneOf( offers.map(_.offerId) )
      val consumer  = randomOneOf(consumers)
      val hash      = TrackingOps.calculateTrackingHash(consumer, offer)
      pgen.apply.copy(
        order_total     = UsebuttonCents( (amt * 100).longValue )
      , id              = randomStringUUID
      , amount          = UsebuttonCents ( (amt * 10).longValue )
      , publisher_customer_id  = Some(hash)
      , commerce_organization  = suiteId
      )
    }

    def generateTxn(amt: Double): UsebuttonTxn = {
      val pload = generatePayload(amt)
      UsebuttonTxn(
        request_id  = randomStringUUID
      , data        = pload
      , id          = pload.id
      , event_type  = randomOneOf( List("tx-validated", "tx-pending") )
      )
    }

    var now = System.currentTimeMillis()

    Stream.unfold[IO, (Int, Double), UsebuttonTxn]( (totalCount, TOTAL_AMOUNT) ) {
      case (remCount, remAmount) =>
        if ( (remCount * 10) % totalCount == 0 ) {
          // val elapsed = ???
          val percentComplete = (totalCount - remCount) * 100 / totalCount
          val passed = System.currentTimeMillis() - now
          val tps    = if (passed > 0) (totalCount - remCount) * 1000 / passed else 0
          println(s"Completed: ${percentComplete}% at ${tps} txn/s")
        }
        remCount match {
          case 0 =>
            None
          case 1 =>
            val amt = remAmount
            Some(generateTxn(amt) -> (0, 0))
          case _ =>
            val amt = remAmount / remCount * ( 1 + randomDouble)
            Some(generateTxn(amt) -> (remCount - 1, remAmount - amt))
        }
    }
  }

}
