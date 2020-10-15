package com.noproject.common.domain.dao.merchant

import java.time.{Clock, Instant}

import cats.data.NonEmptyList
import cats.effect.{IO, LiftIO}
import com.noproject.common.controller.dto.OfferSearchParams
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.{IDAO, JsonConvertible}
import com.noproject.common.domain.model.Location
import com.noproject.common.domain.model.merchant.{MerchantOfferRow, _}
import cats.implicits._
import com.noproject.common.codec.json.ElementaryCodecs._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import io.circe.Json
import io.circe.generic.auto._
import javax.inject.{Inject, Singleton}
import shapeless.{::, HNil}

import scala.language.implicitConversions

@Singleton
class MerchantOfferDAO @Inject()( protected val sp: DefaultPersistence
                                , val clock : Clock ) extends IDAO {

  override type T = MerchantOfferRow

  override val tableName = "merchant_offers"

  override val keyFieldList = List(
    "offer_id"
  )

  override val immFieldList = List(
    "when_activated"
  )

  override val allFieldList = List(
    "offer_id"
  , "offer_description"
  , "offer_location"
  , "offer_address"
  , "merchant_name"
  , "merchant_network"
  , "toc_text"
  , "toc_url"
  , "images"
  , "when_updated"
  , "when_modified"
  , "when_activated"
  , "when_deactivated"
  , "requires_activation"
  , "requires_browser_cookies"
  , "requires_bank_link"
  , "requires_card_link"
  , "requires_geo_tracking"
  , "requires_exclusive"
  , "requires_experimental"
  , "reward_fixed_best"
  , "reward_percent_best"
  , "reward_limit"
  , "reward_currency"
  , "reward_items"
  , "accepted_cards"
  , "tracking_rule"
  , "offer_raw_src"
  )

  override def ginField = "search_index_gin"

  implicit val rewardItemsWrite:Meta[List[MerchantRewardItem]] = JsonConvertible[List[MerchantRewardItem]]

  def update(offer: MerchantOfferRow): IO[Int] = {
    val update = Fragment.const( s"UPDATE $tableName")
    val set = fr"""SET offer_description = ${offer.offerDescription},
                     |    offer_location = ${offer.offerLocation},
                     |    offer_address = ${offer.offerAddress},
                     |    merchant_name = ${offer.merchantName},
                     |    merchant_network = ${offer.merchantNetwork},
                     |    toc_text = ${offer.tocText},
                     |    toc_url = ${offer.tocUrl},
                     |    images = ${offer.images},
                     |    when_activated = ${offer.whenActivated},
                     |    when_deactivated = ${offer.whenDeactivated},
                     |    requires_activation = ${offer.requiresActivation},
                     |    requires_browser_cookies = ${offer.requiresBrowserCookies},
                     |    requires_bank_link = ${offer.requiresBankLink},
                     |    requires_card_link = ${offer.requiresCardLink},
                     |    requires_geo_tracking = ${offer.requiresGeoTracking},
                     |    requires_exclusive = ${offer.requiresExclusive},
                     |    requires_experimental = ${offer.requiresExperimental},
                     |    reward_fixed_best = ${offer.rewardFixedBest},
                     |    reward_percent_best = ${offer.rewardPercentBest},
                     |    reward_limit = ${offer.rewardLimit},
                     |    reward_currency = ${offer.rewardCurrency},
                     |    accepted_cards = ${offer.acceptedCards},
                     |    tracking_rule = ${offer.trackingRule},
                     |    offer_raw_src = ${offer.offerRawSrc},
                     |    when_modified = ${offer.whenModified},
                     |    reward_items = ${offer.rewardItems},
                     |    search_index_gin = to_tsvector('english',${offer.searchIndex})
                     |""".stripMargin
    val where = fr"WHERE offer_id = ${offer.offerId}"

    (update ++ set ++ where).update.run
  }

  def insert(values: List[MerchantOfferRow]): IO[Int] = insertTxn(values)

  def insertTxn(values: List[MerchantOfferRow]): ConnectionIO[Int] = {
    val valuesWithGin = values.map(v => v :: v.searchIndex :: HNil)
    super.insertGin0(valuesWithGin)
  }

  def findAll: IO[List[MerchantOfferRow]] = findAllTxn

  def findAllTxn: ConnectionIO[List[MerchantOfferRow]] = super.findAll0

  def delete(names: NonEmptyList[String]): IO[Int] = {
    val q = Fragment.const(s"delete from $tableName where ")
    val res = q ++ Fragments.in(fr"merchant_name", names)
    res.update.run
  }

  private def getJoinFragment(customerName: Option[String]): Fragment = {
    customerName match {
      case Some(id) =>
        val allFieldsWithTablePrefix = allFieldList.map("o."+_).mkString(", ")
        Fragment.const(s"""select $allFieldsWithTablePrefix
            |from $tableName o
            |inner join customer_networks cn
            |on cn.network_name = o.merchant_network""".stripMargin)
      case None => Fragment.const(s"select $allFields from $tableName")
    }
  }

  def findNew(osp: OfferSearchParams, customerName: Option[String] = None): IO[List[MerchantOfferRow]] = {
    // TODO
    val fSelect = getJoinFragment(customerName)
    val fWhere  = Fragments.whereAndOpt(customerName.map { s => fr"customer_name = $s" })

    val result = fSelect ++ fWhere ++ fr"order by when_activated desc limit ${osp.limit.getOrElse(10)}"
    result.query[MerchantOfferRow].to[List]
  }

  def findFeatured(osp: OfferSearchParams, customerName: Option[String] = None): IO[List[MerchantOfferRow]] = {
    // TODO
    val fSelect = getJoinFragment(customerName)
    val fWhere  = Fragments.whereAndOpt(customerName.map { s => fr"customer_name = $s" })

    val result = fSelect ++ fWhere ++ fr"order by when_activated desc limit ${osp.limit.getOrElse(10)}"
    result.query[MerchantOfferRow].to[List]
  }

  def findNotAvailableOn(moment: Instant, network: String): IO[List[MerchantOfferRow]] = {
    val fr0 = Fragment.const(s"select $allFields from $tableName")
    val fr1 = Fragments.whereAnd(fr"when_updated != $moment", fr"merchant_network = $network")
    (fr0 ++ fr1).query[MerchantOfferRow].to[List]
  }

  def getMetrics(moment: Instant, network: String): IO[MerchantOfferMetrics] = {
    val fr0 = Fragment.const(s"select ")
    val fr1 = fr"  sum(case when when_activated = ${moment} then 1 else 0 end) as created"
    val fr2 = fr", sum(case when when_modified = ${moment} and when_activated != ${moment} then 1 else 0 end) as updated"
    val fr3 = fr", sum(case when when_modified != ${moment} and when_activated != ${moment} then 1 else 0 end) as unchanged"
    val fr4 = fr", sum(case when when_deactivated = ${moment} and when_modified = ${moment} then 1 else 0 end) as deactivated"
    val fr5 = fr", sum(case when when_deactivated is null or when_deactivated > ${moment} then 1 else 0 end) as active"
    val fr6 = fr", sum(case when when_deactivated <= ${moment} then 1 else 0 end) as inactive"
    val fr7 = fr", count(*) as total"
    val fr8 = Fragment.const(s" from $tableName")
    val fr9 = Fragments.whereAnd(fr"merchant_network = $network")
    val qry = (fr0 ++ fr1 ++ fr2 ++ fr3 ++ fr4 ++ fr5 ++ fr6 ++ fr7 ++ fr8 ++ fr9)
    qry.query[MerchantOfferMetrics].unique
  }

  def getFreshnessData: IO[MerchantOfferFreshness] = {
    val fr0 = Fragment.const(s"select merchant_network, max(when_updated) from $tableName group by merchant_network")
    fr0.query[(String, Instant)].to[List].map { elements =>
      val res = elements.foldLeft(Map[String, Instant]())(_+_)
      MerchantOfferFreshness(res)
    }
  }

  def find(osp: OfferSearchParams, customerName: Option[String] = None): IO[List[MerchantOfferRow]] = {
    // cause fields sequence in ddl and in allFields are not the same, so we can't use 'select * '
    val allFieldsWithTablePrefix = allFieldList.map("o."+_).mkString(", ")

    val query = customerName match {
      case Some(id) =>
        Fragment.const(s"""select $allFieldsWithTablePrefix, m.categories
                          |from $tableName o
                          |inner join merchants         m  on m.merchant_name = o.merchant_name
                          |inner join customer_networks cn on cn.network_name = o.merchant_network""".stripMargin)
      case None =>
        Fragment.const(s"""select $allFieldsWithTablePrefix, m.categories
                          |from $tableName o
                          |inner join merchants m on m.merchant_name = o.merchant_name""".stripMargin)
    }

    val fcustomer = customerName.map{ s => fr"cn.customer_name = $s" }

    val ftype = osp.purchaseOnline match {
      case None        => None
      case Some(true)  => Some(Fragment.const(s"o.tracking_rule is not null"))
      case Some(false) => Some(Fragment.const(s"o.tracking_rule is null"))
    }

    val fsearch = osp.search.map { s =>
      val tsQuerySource = s.filter(l => l.isLetterOrDigit || l.isWhitespace)
      val foffrindex = Fragment.const(s"o.search_index_gin @@ to_tsquery('english', '''$tsQuerySource''')")
      val fmercindex = Fragment.const(s"m.search_index_gin @@ to_tsquery('english', '''$tsQuerySource''')")
      Fragments.or(foffrindex, fmercindex)
    }

    val fnames = osp.names.map { rule =>
      if (rule.include) Fragments.in(fr"o.merchant_name", rule.values)
      else              Fragments.notIn(fr"o.merchant_name", rule.values)
    }

    val ftags = osp.tags.map { rule =>
      if (rule.include) arrayContains(fr"m.categories", rule.values, "text")
      else              arrayContainsNot(fr"m.categories", rule.values, "text")
    }

    val fnets = osp.networks.map { rule =>
      if (rule.include) Fragments.in(fr"o.merchant_network", rule.values)
      else              Fragments.notIn(fr"o.merchant_network", rule.values)
    }

    val fids = osp.ids.map { rule =>
      if (rule.include) Fragments.in(fr"o.offer_id", rule.values)
      else              Fragments.notIn(fr"o.offer_id", rule.values)
    }

    val fbegin  = osp.activeFrom.map { s => fr"when_deactivated > $s or when_deactivated is null" }
    val fend    = osp.activeTo.map { s => fr"when_activated < $s" }

    val factivated = Fragments.andOpt(fbegin, fend)
    val factivatedOpt = if (factivated == Fragment.empty) None else Some(factivated)

    val fnb = osp.nearby.map { circle =>
      val lng = circle.center.lon
      val lat = circle.center.lat
      Fragment.const(s"ST_DWithin(ST_GeomFromEWKT('SRID=4326;POINT($lng $lat)'), o.offer_location, ${circle.radius}, true)")
    }

    val where = Fragments.whereAndOpt(fcustomer, fsearch, ftype, fnames, ftags, fnets, fids, factivatedOpt, fnb)
    var result = query ++ where

    osp.limit.foreach { lim => result ++= fr"LIMIT ${lim} OFFSET ${osp.offset.getOrElse(0)}" }

    result.query[MerchantOfferRow].to[List]
  }

  def markUpdated(offerIds: List[String], moment: Instant): IO[Int] = {
    offerIds match {
      case Nil =>
        IO.pure(0)
      case _ =>
        val nel = NonEmptyList.fromListUnsafe(offerIds)
        val f1 = Fragment.const(s"update $tableName")
        val f2 = fr"set when_updated = $moment"
        val f3 = Fragments.whereAnd(Fragments.in(fr"offer_id", nel))
        val sql = f1 ++ f2 ++ f3
        sql.update.run
    }
  }

  def markInactive(moment: Instant, offerIds: List[String]): IO[Int] = {
    offerIds match {
      case Nil =>
        IO.pure(0)
      case _ =>
        val nel = NonEmptyList.fromListUnsafe(offerIds)
        val f1 = Fragment.const(s"update $tableName")
        val f2 = fr"set when_deactivated = $moment"
        val f3 = Fragments.whereAnd(Fragments.in(fr"offer_id", nel))
        val sql = f1 ++ f2 ++ f3
        sql.update.run
    }
  }


  def exist(strings: NonEmptyList[String]): IO[List[String]] = {
    val fr1 = Fragment.const(s"select offer_id from $tableName where")
    val fr2 = Fragments.in(fr"offer_id", strings)
    (fr1 ++ fr2).query[String].to[List]
  }

  def getCategories(customerName: Option[String] = None): IO[List[String]] = {
    val sql = customerName match {
      case None     => Fragment.const("select distinct unnest(categories) from merchants")
      case Some(id) => Fragment.const(
        s"""
           |select distinct unnest(categories)
           |from merchants m
           |  join merchant_offers o on m.merchant_name = o.merchant_name
           |  join customer_networks n on n.network_name = o.merchant_network
           |where n.customer_name = '$id'
         """.stripMargin)
    }
    sql.query[String].to[List]
  }

  def findById(offerId: String): ConnectionIO[Option[MerchantOfferRow]] = {
    val sql = s"select $allFields from $tableName where offer_id = ?"
    Query[String, MerchantOfferRow](sql).toQuery0(offerId).option
  }
}
