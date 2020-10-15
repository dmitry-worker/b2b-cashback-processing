package com.noproject.domain.dao.crap

import cats.effect.IO
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import doobie.util.update.Update
import javax.inject.Inject

class CrapDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {
  override def tableName: String = ""

  override def keyFieldList: List[String] = Nil

  override def allFieldList: List[String] = Nil

  def deleteCrapTxns(ref: String): IO[Int] = {
    val query  = s"delete from cashback_transaction where description = ?"
    Update[String](query).toUpdate0(ref).run
  }

  def deleteCrapMerchants(ref: String): IO[Int] = {
    val query  = s"delete from cashback_transaction where description = ?"
    Update[String](query).toUpdate0(ref).run
  }

  def deleteCrapOffers(ref: String): IO[Int] = {
    val query  = s"delete from merchant_offers where offer_description = ?"
    Update[String](query).toUpdate0(ref).run
  }
}
