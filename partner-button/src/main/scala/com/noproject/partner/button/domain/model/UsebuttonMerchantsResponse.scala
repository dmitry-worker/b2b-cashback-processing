package com.noproject.partner.button.domain.model

import java.net.URLEncoder

case class UsebuttonMerchantsResponse(meta: UsebuttonMeta, objects: List[UsebuttonMerchantResponseItem])

case class UsebuttonMerchantResponseItem(
  id:                  String
, name:                String
, categories:          List[String]
, metadata:            Map[String, String]
, urls:                UsebuttonMerchantUrls
, available_platforms: List[String]
, supported_products:  List[String]
, status:              String
) {
  // we don't need to use usebutton links api, we can build them manually, look at this guide:
  // https://developer.usebutton.com/guides/publishers/mobile-web/button-publisher-integration-guide
  lazy val offerLink = "https://r.bttn.io?btn_pub_user={userId}&btn_url={merchantUrl}&btn_ref={organizationId}"

  def toMerchant(offers: List[UsebuttonOffer], bestOffer: Option[String], orgId: String): UsebuttonMerchant = {
    val link = offerLink
      .replace("{organizationId}", orgId)
      .replace("{merchantUrl}", URLEncoder.encode(urls.homepage, "UTF-8"))
    UsebuttonMerchant(
      id                  = this.id
    , name                = this.name
    , categories          = this.categories
    , metadata            = this.metadata
    , urls                = this.urls
    , availablePlatforms  = this.available_platforms
    , supportedProducts   = this.supported_products
    , status              = this.status
    , revenueSharePercent = 0
    , offers              = offers
    , bestOfferId         = bestOffer
    , offerLink           = link
    )
  }
}

/*
example:

{
    "meta": {
        "status": "ok"
    },
    "objects": [
        {
            "id": "org-228b55a5707de5c8",
            "name": "Button Test Merchant (Sandbox)",
            "categories": [
                "E-Commerce"
            ],
            "metadata": {
                "description": "Button's Test Merchant",
                "icon_url": "https://button.imgix.net/org-228b55a5707de5c8/icon/37852d867dd8185c.jpg",
                "banner_url": "not supported yet"
            },
            "urls": {
                "homepage": "https://buttontestmerchant.com",
                "terms_and_conditions": null
            },
            "available_platforms": [
                "web",
                "ios",
                "android"
            ],
            "supported_products": [],
            "status": "approved",
            "additional_terms": null,
            "exclusion_details": null
        }
    ]
}
*/
