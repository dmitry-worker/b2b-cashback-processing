package com.noproject.common.domain.service

import cats.effect.IO
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings

trait MerchantMappingDataService {

  def getMappings: IO[MerchantMappings]

  def getNameMappings: IO[Map[String, String]]

  def getCategoryMappings: IO[Map[String, Seq[String]]]

}
