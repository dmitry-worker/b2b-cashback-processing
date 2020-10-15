package com.noproject.modules

import com.google.inject.{AbstractModule, TypeLiteral}
import com.noproject.common.cache.KeyValueCache
import com.noproject.common.domain.model.merchant.MerchantOfferRow
import com.noproject.common.domain.service.ConsumerDataService
import com.noproject.service.TrackingService
import net.codingwell.scalaguice.ScalaModule

class TrackingModule(cache: KeyValueCache[String, MerchantOfferRow]) extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[ConsumerDataService]
    bind[TrackingService]
    bind(new TypeLiteral[KeyValueCache[String, MerchantOfferRow]](){}).toInstance(cache)
  }
}
