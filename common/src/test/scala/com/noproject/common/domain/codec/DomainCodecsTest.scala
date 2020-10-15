package com.noproject.common.domain.codec

import org.scalatest.{Matchers, WordSpec}
import com.noproject.common.data.gen.Generator
import DomainCodecs._
import com.noproject.common.domain.model.Money
import com.noproject.common.domain.model.transaction.CashbackTransaction
import io.circe.Json

class DomainCodecsTest extends WordSpec with Matchers {

  "DomainCodecs" should {
    "apply a pre-defined diff" in {
      val txn = genTxn.copy(cashbackUserUSD = Money(0))
      val copy = txn.copy(cashbackUserUSD = Money(10))
      assert(txnDiff != null)
      val json = txnDiff.calculate(txn, copy)
      val previous = json.hcursor.downField("cashbackUserUSD").as[Int]
      previous.right.get shouldEqual 0
    }
  }

  implicit def genJson:Generator[Json] = new Generator[Json] {
    override def apply: Json = Json.fromFields(
      List("fieldName" -> Json.fromString("fieldValue"))
    )
  }

  def genTxn: CashbackTransaction = Generator[CashbackTransaction].apply

}
