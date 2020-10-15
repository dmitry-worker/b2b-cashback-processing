package com.noproject.common.domain.model.merchant

import java.time.Instant

import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import com.noproject.common.security.Hash

trait MerchantItem {

  def asMerchant(mappings: MerchantMappings): MerchantRow
  def asOffer(mappings: MerchantMappings, moment: Instant): MerchantOfferRow
  def name:         String
  def qualifiedNetwork: String

  def qualifiedName(mappings: MerchantMappings): String = {
    mappings.remapName(this.name)
  }

  def qualifiedId(mappings: MerchantMappings): String = {
    qualifiedNetwork + ":" + Hash.md5.hex(qualifiedName(mappings))
  }

}
