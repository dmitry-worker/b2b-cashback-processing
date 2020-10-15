package com.noproject.common.domain.model.transaction

import java.time.Instant
import java.time.temporal.ChronoField

import cats.kernel.Eq
import com.noproject.common.Decimals
import com.noproject.common.Exceptions.LoggableException
import com.noproject.common.domain.codec.DomainCodecs._
import com.noproject.common.domain.model.{GinWrapper, Money}
import com.noproject.common.domain.model.transaction.CashbackTxnStatus._
import com.noproject.common.domain.model.eventlog.{EventLogItem, EventLogObjectType, Loggable}
import io.chrisdavenport.fuuid.FUUID
import io.circe.Json

case class CashbackTransaction(
  id:                 FUUID
, userId:             String
, customerName:       String
, reference:          String
, merchantName:       String
, merchantNetwork:    String
, description:        Option[String]
, whenClaimed:        Option[Instant]
, whenSettled:        Option[Instant]
, whenPosted:         Option[Instant]
, purchaseDate:       Instant
, purchaseAmount:     Money
, purchaseCurrency:   String
, cashbackBaseUSD:    Money
, cashbackTotalUSD:   Money
, cashbackUserUSD:    Money
, cashbackOwnUSD:     Money
, status:             CashbackTxnStatus
, parentTxn:          Option[FUUID]
, payoutId:           Option[String]
, failedReason:       Option[String]
, rawTxn:             Json
, offerId:            Option[String]
, offerTimestamp:     Option[Instant]
// Bookkeeping fields must be at the end.
, whenCreated:        Instant
, whenUpdated:        Instant
) extends Loggable with GinWrapper {

  lazy val asJson = txnEnc.apply(this)

  lazy val txnKey = new TxnKey(reference, merchantNetwork)

  override def searchIndex: String = merchantName

  def setNow(now: Instant): CashbackTransaction = {
    this.copy( whenCreated = now, whenUpdated = now )
  }

  def valid: Either[LoggableException[CashbackTransaction], CashbackTransaction] = {
    import scala.concurrent.duration._

    if (purchaseAmount.equals(Money.zero))
      Left(LoggableException(this, "Purchase amount cannot be equal to 0"))
    else if (purchaseDate.isBefore(Instant.now.minusSeconds(365.days.toSeconds)))
      Left(LoggableException(this, "Purchase date cannot be older than 1 year"))
    else if (purchaseDate.isAfter(Instant.now))
      Left(LoggableException(this, "Purchase date cannot be greater than current date"))
    else Right(this)

  }

  def possibleStatusChanges: Set[CashbackTxnStatus] = {
    status match {
      case Pending    => Set(Pending, Available, Paid, Rejected, Expired)
      case Available  => Set(Available, Paid, Rejected, Expired)
      case Paid       => Set.empty
      case Rejected   => Set.empty
      case Expired    => Set.empty
    }
  }

  override def eventLogType: EventLogObjectType = EventLogObjectType.CashbackTxn

  override def asEventLogItem(message: String, details: Option[String] = None): EventLogItem = {
    val objid = id.toString
    EventLogItem(None, Instant.now.`with`(ChronoField.NANO_OF_SECOND, 0L), eventLogType, Some(objid), Some(this.asJson), message, details)
  }
}

object CashbackTransaction {
  implicit val eq = Eq.instance[CashbackTransaction] { (f, s) =>
    f.id == s.id &&
    f.userId == s.userId &&
    f.customerName == s.customerName &&
    f.reference == s.reference &&
    f.merchantName == s.merchantName &&
    f.merchantNetwork == s.merchantNetwork &&
    f.description == s.description &&
    f.whenClaimed == s.whenClaimed &&
    f.whenSettled == s.whenSettled &&
    f.whenPosted == s.whenPosted &&
    f.purchaseDate == s.purchaseDate &&
    f.purchaseAmount == s.purchaseAmount &&
    f.purchaseCurrency == s.purchaseCurrency &&
    f.cashbackBaseUSD == s.cashbackBaseUSD &&
    f.cashbackTotalUSD == s.cashbackTotalUSD &&
    f.cashbackUserUSD == s.cashbackUserUSD &&
    f.cashbackOwnUSD == s.cashbackOwnUSD &&
    f.status == s.status &&
    f.parentTxn == s.parentTxn &&
    f.payoutId == s.payoutId &&
    f.offerId == s.offerId &&
    f.offerTimestamp == s.offerTimestamp
  }
}

