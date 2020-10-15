package com.noproject.common.domain.service

import cats.effect.IO
import com.noproject.common.Exceptions.OfferNotFoundException
import com.noproject.common.cache.KeyValueCache
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.merchant.MerchantOfferDAO
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import javax.inject.{Inject, Singleton}
import doobie.implicits._

@Singleton
class MerchantCacheService @Inject()(
  moDAO: MerchantOfferDAO
, sp:    DefaultPersistence
, cache: KeyValueCache[String, MerchantOfferRow]
){

  private def loadOfferRow(id: String): IO[MerchantOfferRow] = {
    moDAO.findById(id).transact(sp.xar).map {
      case Some(offer) => offer
      case None => throw OfferNotFoundException(id)
    }
  }

  def findOfferRowById(id: String): IO[MerchantOfferRow] = {
    cache.applyOrElse(id, loadOfferRow(id))
  }


}
