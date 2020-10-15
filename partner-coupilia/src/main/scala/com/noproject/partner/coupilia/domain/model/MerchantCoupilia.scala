package com.noproject.partner.coupilia.domain.model

import com.noproject.common.domain.model.merchant.MerchantRow
import com.noproject.common.domain.model.merchant.mapping.MerchantMappings
import enumeratum._

case class MerchantCoupilia(
  id:                 Long
, name:               String
, logo:               Option[String]
, url:                Option[String]
, networkid:          Option[String]
, website:            Option[String]
, network:            Option[String]
, keywords:           Option[String]
, country:            String
, commission:         Double
, commissiontype:     CoupiliaCommissionType
, affiliatedurl:      Boolean
, categories:         List[MerchantCoupiliaCategory]
, commissioncurrency: String
, shiptocountries:    Option[String]
) {

  def asMerchantRow(mappings: MerchantMappings): MerchantRow = {
    MerchantRow(
      merchantName = mappings.remapName(name)//String
    , description  = ""//String
    , logoUrl      = logo.getOrElse("http://logo.absent")//String
    , imageUrl     = None//Option[String]
    , categories   = categories.map(_.primary)//List[String]
    , priceRange   = None//Option[String]
    , website      = website//Option[String]
    , phone        = None//Option[String]
    )
  }

}

sealed trait CoupiliaCommissionType extends EnumEntry
object CoupiliaCommissionType extends Enum[CoupiliaCommissionType] {
  case object Percentage  extends CoupiliaCommissionType
  case object FlatRate    extends CoupiliaCommissionType
  override def values: scala.collection.immutable.IndexedSeq[CoupiliaCommissionType] = findValues
}

case class MerchantCoupiliaUrl(
  location:   String
, label:      Option[String]
)

case class MerchantCoupiliaCategory(
  primary:    String
, secondary:  Option[List[String]]
)
