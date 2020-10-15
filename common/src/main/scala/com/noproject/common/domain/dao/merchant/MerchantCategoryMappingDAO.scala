package com.noproject.common.domain.dao.merchant

import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.IDAO
import com.noproject.common.domain.model.merchant.mapping.MerchantCategoryMapping
import javax.inject.{Inject, Singleton}

@Singleton
class MerchantCategoryMappingDAO @Inject()(protected val sp: DefaultPersistence) extends IDAO {

  override type T = MerchantCategoryMapping

  override val tableName = "merchant_category_mappings"

  override val keyFieldList = List(
    "foreign_category"
  )

  override val allFieldList = List(
    "foreign_name"
  , "common_name"
  )

}