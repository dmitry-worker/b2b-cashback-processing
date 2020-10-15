package com.noproject.domain.model.merchant.category

case class MerchantCategoryRow(
  id: String,
  name: String,
  imageUrl: String,
  isTop: Boolean,
  viewPriority: Int
)
