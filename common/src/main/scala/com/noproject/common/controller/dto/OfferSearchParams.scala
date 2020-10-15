package com.noproject.common.controller.dto

import java.time.Instant

import cats.data.NonEmptyList
import com.noproject.common.domain.model.Circle



case class OfferSearchParams(
  search:         Option[String] = None
, nearby:         Option[Circle] = None
, limit:          Option[Int] = None
, offset:         Option[Int] = None
, names:          Option[SearchParamsRule[String]] = None
, tags:           Option[SearchParamsRule[String]] = None
, ids:            Option[SearchParamsRule[String]] = None
, networks:       Option[SearchParamsRule[String]] = None
, activeFrom:     Option[Instant] = None
, activeTo:       Option[Instant] = None
, purchaseOnline: Option[Boolean] = None
) {

  def withLimit(n: Int): OfferSearchParams = this.copy(limit = Some(n))

  def withOffset(n: Int): OfferSearchParams = this.copy(offset = Some(n))

  def withRule(key: String, spr: SearchParamsRule[String]): OfferSearchParams = {
    key match {
      case "names"    => this.copy(names = Some(spr.update(this.names)))
      case "tags"     => this.copy(tags = Some(spr.update(this.tags)))
      case "ids"      => this.copy(ids = Some(spr.update(this.ids)))
      case "networks" => this.copy(networks = Some(spr.update(this.networks)))
      case x          => throw new IllegalArgumentException(s"Unexpected rule key: ${x}")
    }
  }

  def withNetworks(ns: NonEmptyList[String]): OfferSearchParams = withRule("networks", SearchParamsRule(true, ns))
  def withoutNetworks(ns: NonEmptyList[String]): OfferSearchParams = withRule("networks", SearchParamsRule(false, ns))

  def withNames(ns: NonEmptyList[String]): OfferSearchParams = withRule("names", SearchParamsRule(true, ns))
  def withoutNames(ns: NonEmptyList[String]): OfferSearchParams = withRule("names", SearchParamsRule(false, ns))

  def withIds(ns: NonEmptyList[String]): OfferSearchParams = withRule("ids", SearchParamsRule(true, ns))
  def withoutIds(ns: NonEmptyList[String]): OfferSearchParams = withRule("ids", SearchParamsRule(false, ns))

  def withTags(ns: NonEmptyList[String]): OfferSearchParams = withRule("tags", SearchParamsRule(true, ns))
  def withoutTags(ns: NonEmptyList[String]): OfferSearchParams = withRule("tags", SearchParamsRule(false, ns))

}


