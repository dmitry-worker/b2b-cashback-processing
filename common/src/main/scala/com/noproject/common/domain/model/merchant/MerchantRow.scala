package com.noproject.common.domain.model.merchant

import cats.Show
import cats.kernel.Eq
import com.noproject.common.domain.model.GinWrapper
import io.circe.generic.auto._
import io.circe.syntax._

case class MerchantRow(
  merchantName: String
, description:  String
, logoUrl:      String
, imageUrl:     Option[String]
, categories:   List[String]
, priceRange:   Option[String]
, website:      Option[String]
, phone:        Option[String]
) extends GinWrapper {
  def extern(offers: List[MerchantOfferRow], baseUrl: String, customer: String): Merchant = {
    Merchant(
      merchantName = this.merchantName
    , description  = this.description
    , logoUrl      = this.logoUrl
    , imageUrl     = this.imageUrl
    , categories   = this.categories
    , priceRange   = this.priceRange
    , website      = this.website
    , phone        = this.phone
    , offers       = offers.map(_.extern(baseUrl, customer))
    )
  }

  override def searchIndex: String = (List(merchantName, description) ++ categories).mkString(". ")

}

object MerchantRow {

  implicit val eq = Eq.instance[MerchantRow] { (f,s) =>
     ( f.merchantName == s.merchantName
    && f.description == s.description
    && f.logoUrl == s.logoUrl
    && f.imageUrl == s.imageUrl
    && f.categories == s.categories
    && f.priceRange == s.priceRange
    && f.website == s.website
    && f.phone == s.phone )
  }

  private val priceRangeRegexp = "\\$+".r

  def priceRangeToNumber(prDollars: Option[String]): Option[Int] =
    prDollars match {
      case Some(v) if v.matches(priceRangeRegexp.regex) =>
        Some(priceRangeRegexp.findAllMatchIn(v).size)
      case _                                            =>
        None
    }

}