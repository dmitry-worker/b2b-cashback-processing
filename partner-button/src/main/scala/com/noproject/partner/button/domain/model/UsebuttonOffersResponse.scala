package com.noproject.partner.button.domain.model

case class UsebuttonOffersResponse(meta: UsebuttonMeta, `object`: UsebuttonOffersResponseObject)

case class UsebuttonOffersResponseObject(merchant_offers: List[UsebuttonOffersResponseItem])

case class UsebuttonOffersResponseItem(
  merchant_id:   String
, best_offer_id: Option[String]
, offers:        List[UsebuttonOfferItem]
)

case class UsebuttonOfferItem(
  id:             String
, rate_percent:   Option[BigDecimal]
, rate_fixed:     Option[BigDecimal]
, display_params: Map[String, String]
) {
  val defaultRewardCategory = "Reward"
  def asUsebuttonOffer: UsebuttonOffer = UsebuttonOffer(id, rate_percent, rate_fixed, display_params.get("category"))
}


/*
example:

{
    "meta": {
        "status": "ok"
    },
    "object": {
        "merchant_offers": [
            {
                "merchant_id": "org-228b55a5707de5c8",
                "best_offer_id": "offer-5cd2c3107c0a0e3e-0",
                "offers": [
                    {
                        "id": "offer-5cd2c3107c0a0e3e-0",
                        "rate_percent": "10",
                        "display_params": {
                            "category": "Toys"
                        }
                    },
                    {
                        "id": "offer-5cd2c3107c0a0e3e-1",
                        "rate_percent": "5",
                        "display_params": {
                            "category": "Furniture"
                        }
                    },
                    {
                        "id": "offer-5cd2c3107c0a0e3e-2",
                        "rate_percent": "2",
                        "display_params": {
                            "category": "Electronics"
                        }
                    }
                ]
            }
        ]
    }
}
*/
