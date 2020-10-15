package com.noproject.stress.data.gen

import java.time.Instant

import cats.effect.IO
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.model.customer.Consumer
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import com.noproject.partner.azigo.domain.model.AzigoTxn
import com.noproject.service.TrackingOps
import com.noproject.stress.config.StressTestPartnerConfig
import fs2.Stream
import io.circe.Json


class AzigoGenerator(
  stpc:       StressTestPartnerConfig
, offers:     List[MerchantOfferRow]
, consumers:  List[Consumer]
, suiteId:    String
) extends StreamingGenerator[AzigoTxn] with RandomValueGenerator {
  require( offers.nonEmpty )

  val customer = s"testCustomer"
  val (_, totalCount, totalAmount) = StressTestPartnerConfig.unapply(stpc).get
  val TOTAL_CONSUMERS = totalCount / 10
  val TOTAL_AMOUNT = totalCount * 100D

  def genStream: Stream[IO, AzigoTxn] = {

    implicit def genJson:Generator[Option[Json]] = new Generator[Option[Json]] {
      override def apply: Option[Json] = None
    }

    implicit def instantGen: Generator[Instant] = new Generator[Instant] {
      override def apply: Instant = {
        randomInstantInRange(Instant.now.minusSeconds(86400), Instant.now)
      }
    }
    val gen = Generator[AzigoTxn]

    def generateTxn(amt: Double) = {
      val offer     = randomOneOf( offers.map(_.offerId) )
      val consumer  = randomOneOf(consumers)
      val hash      = TrackingOps.calculateTrackingHash(consumer, offer)
      gen.apply.copy(
        sale            = amt
      , uniqueRecordId  = randomStringUUID
      , commission      = amt / 10
      , suppliedUserId  = Some(hash)
      , programName     = suiteId
      )
    }

    var now = System.currentTimeMillis()

    Stream.unfold[IO, (Int, Double), AzigoTxn]( (totalCount, TOTAL_AMOUNT) ) {
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
