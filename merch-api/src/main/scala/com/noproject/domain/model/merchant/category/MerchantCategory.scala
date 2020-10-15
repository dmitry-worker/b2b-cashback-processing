package com.noproject.domain.model.merchant.category

import io.swagger.annotations.ApiModelProperty

import scala.annotation.meta.field

object Categories {

  case class InstoreCategories(
                                @(ApiModelProperty @field)(value = "Main category of the venue")
                                main: InstoreCategory,
                                @(ApiModelProperty @field)(value = "Sub category of the venue", required = false)
                                sub: Option[InstoreCategory]
                              )

  case class InstoreCategory(
                              name: String,
                              id: String,
                              parent: Option[String]
                            )

  //instore
  val Restaurants = InstoreCategory("Restaurants", "restaurants", None)
  val Stores = InstoreCategory("Stores", "stores", None)
  val Services = InstoreCategory("Services", "services", None)
  val Entertainment = InstoreCategory("Entertaiment", "entertaiment", None)

  //subs
  val InstoreSubBar = InstoreCategory("Bar","bar", Some(Restaurants.id))
  val InstoreSubFineDining = InstoreCategory("Fine Dining","fineDining", Some(Restaurants.id))
  val InstoreSubDesserts = InstoreCategory("Desserts","desserts", Some(Restaurants.id))
  val InstoreSubPizza = InstoreCategory("Pizza","pizza", Some(Restaurants.id))
  val InstoreSubSushiBar = InstoreCategory("Sushi Bar","sushiBar", Some(Restaurants.id))
  val InstoreSubIndian = InstoreCategory("Indian","indian", Some(Restaurants.id))
  val InstoreSubItalian = InstoreCategory("Italian","italian", Some(Restaurants.id))
  val InstoreSubAmericanNew = InstoreCategory("American (New)","americanNew", Some(Restaurants.id))
  val InstoreSubVegetarian = InstoreCategory("Vegetarian","vegetarian", Some(Restaurants.id))
  val InstoreSubBurgers = InstoreCategory("BurgersBar","burgers", Some(Restaurants.id))
  val InstoreSubFrench = InstoreCategory("French","french", Some(Restaurants.id))
  val InstoreSubBrunch = InstoreCategory("Brunch","brunch", Some(Restaurants.id))
  val InstoreSubBreakfast = InstoreCategory("Breakfast","breakfast", Some(Restaurants.id))
  val InstoreSubSeafood = InstoreCategory("Seafood","seafood", Some(Restaurants.id))
  val InstoreSubMexican = InstoreCategory("Mexican","mexican", Some(Restaurants.id))
  val InstoreSubMediterranean = InstoreCategory("Mediterranean","mediterranean", Some(Restaurants.id))
  val InstoreSubSubsAndSandwiches = InstoreCategory("Subs & Sandwiches","subsAndSandwiches", Some(Restaurants.id))
  val InstoreSubWineBar = InstoreCategory("Wine Bar","wineBar", Some(Restaurants.id))
  val InstoreSubBrewery = InstoreCategory("Brewery","brewery", Some(Restaurants.id))
  val InstoreSubAmericanTraditional = InstoreCategory("American (Traditional)","americanTraditional", Some(Restaurants.id))
  val InstoreSubSportsBar = InstoreCategory("Sports Bar","sportsBar", Some(Restaurants.id))
  val InstoreSubCoffeeAndTea = InstoreCategory("Coffee & Tea","coffeeAndTea", Some(Restaurants.id))
  val InstoreSubDiner = InstoreCategory("Diner","diner", Some(Restaurants.id))
  val InstoreSubThai = InstoreCategory("Thai","thai", Some(Restaurants.id))
  val InstoreSubAsianFusion = InstoreCategory("Asian Fusion","asianFusion", Some(Restaurants.id))
  val InstoreSubDeli = InstoreCategory("Deli","deli", Some(Restaurants.id))
  val InstoreSubFastFood = InstoreCategory("Fast Food","fastFood", Some(Restaurants.id))
  val InstoreSubBarbeque = InstoreCategory("Barbeque","barbeque", Some(Restaurants.id))

  //online
  val Clothing: InternalMerchantCategory = createCategory("Clothing")
  val Groceries: InternalMerchantCategory = createCategory("Groceries")
  val Footwear: InternalMerchantCategory = createCategory("Footwear")
  val HealthBeauty: InternalMerchantCategory = createCategory("Health & Beauty")
  val Electronics: InternalMerchantCategory = createCategory("Electronics")
  val KidsToddlers: InternalMerchantCategory = createCategory("Kids & Toddlers")
  val SportsOutdoor: InternalMerchantCategory = createCategory("Sports & Outdoor")
  val HomeGarder: InternalMerchantCategory = createCategory("Home & Garden")
  val Pets: InternalMerchantCategory = createCategory("Pets")
  val EatingOut: InternalMerchantCategory = createCategory("Eating Out")
  val Entertaiment: InternalMerchantCategory = createCategory("Entertainment")
  val Fuel: InternalMerchantCategory = createCategory("Fuel")
  val Miscellaneous: InternalMerchantCategory = createCategory("Miscellaneous")
  val Travel: InternalMerchantCategory = createCategory("Travel")

  object InternalMerchantCategory {
    def fromMerchantCategoryRow(row: MerchantCategoryRow): InternalMerchantCategory = {
      InternalMerchantCategory(
        id = row.id,
        name = row.name
      )
    }
  }

  case class InternalMerchantCategory(
                               @(ApiModelProperty @field)(value = "Category id")
                               id: String,
                               @(ApiModelProperty @field)(value = "Category name")
                               name: String
                             )

  case class PartnerCategory(
                              id: String,
                              network: String,
                              name: String,
                              merchantCategory: InternalMerchantCategory
                            )

  object PartnerCategory {
    def apply(
        name: String,
        merchantCategory: Option[InternalMerchantCategory],
        network: String
    ): Option[PartnerCategory] = {
      merchantCategory.map { merchCat =>
        PartnerCategory(
          id = normalizeCategoryName(name),
          network = network,
          name = name,
          merchantCategory = merchCat
        )
      }
    }
  }

  case class CategoriesResponse(
                                 @(ApiModelProperty @field)(value = "List of available categories")
                                 categories: Seq[InternalMerchantCategory]
                               )

  def toTuple(merchantCategory: InternalMerchantCategory): Tuple2[String, InternalMerchantCategory] =
    merchantCategory.id -> merchantCategory

  def toTuple(partnerCategory: PartnerCategory): Tuple2[String, PartnerCategory] =
    partnerCategory.id -> partnerCategory

  def normalizeCategoryName(name: String): String = {
    val split = name.split(" ").map(_.replaceAll("[^a-zA-Z]", ""))
    (split.take(1).map(_.toLowerCase) ++ split.drop(1).map(_.capitalize)).mkString("")
  }

  private def createCategory(name: String): InternalMerchantCategory =
    InternalMerchantCategory(
      id = normalizeCategoryName(name),
      name = name
    )
}