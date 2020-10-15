package com.noproject.partner.button.route


object UsebuttonWebhookRequests {

  val someJson = "\n{\n  \"request_id\": \"attempt-XXX\",\n  \"data\": {\n    \"posting_rule_id\": null,\n    \"order_currency\": \"USD\",\n    \"modified_date\": \"2017-01-01T20:00:00.000Z\",\n    \"created_date\": \"2017-01-01T20:00:00.000Z\",\n    \"order_line_items\": [\n        {\n            \"identifier\": \"sku-1234\",\n            \"total\": 6000,\n            \"amount\": 2000,\n            \"quantity\": 3,\n            \"publisher_commission\": 1000,\n            \"sku\": \"sku-1234\",\n            \"upc\": \"400000000001\",\n            \"category\": [\"Clothes\"],\n            \"description\": \"T-shirts\",\n            \"attributes\":{\n                \"size\": \"M\"\n            }\n        }\n    ],\n    \"button_id\": \"btn-XXX\",\n    \"campaign_id\": \"camp-XXX\",\n    \"rate_card_id\": \"ratecard-XXX\",\n    \"order_id\": \"order-1\",\n    \"customer_order_id\": \"abcdef-123456\",\n    \"account_id\": \"acc-XXX\",\n    \"btn_ref\": \"srctok-XXYYZZ\",\n    \"currency\": \"USD\",\n    \"pub_ref\": \"publisher-token\",\n    \"status\": \"validated\",\n    \"event_date\": \"2017-01-01T20:00:00Z\",\n    \"order_total\": 6000,\n    \"advertising_id\": \"aaaaaaaa-1111-3333-4444-999999999999\",\n    \"publisher_organization\": \"org-XXX\",\n    \"commerce_organization\": \"org-XXX\",\n    \"amount\": 1000,\n    \"button_order_id\": \"btnorder-XXX\",\n    \"publisher_customer_id\": \"10101\",\n    \"id\": \"tx-XXX\",\n    \"order_click_channel\": \"app\",\n    \"category\": \"new-user-order\",\n    \"validated_date\": \"2017-01-06T19:02:09Z\"\n  },\n  \"id\": \"hook-XXX\",\n  \"event_type\": \"tx-validated\"\n}\n    "


  val correctJsonRequest =
    """
      |{
      |  "request_id": "attempt-XXX",
      |  "data": {
      |    "posting_rule_id": null,
      |    "order_currency": "USD",
      |    "modified_date": "2017-01-01T20:00:00.000Z",
      |    "created_date": "2017-01-01T20:00:00.000Z",
      |    "order_line_items": [
      |        {
      |            "identifier": "sku-1234",
      |            "total": 6000,
      |            "amount": 2000,
      |            "quantity": 3,
      |            "publisher_commission": 1000,
      |            "sku": "sku-1234",
      |            "upc": "400000000001",
      |            "category": ["Clothes"],
      |            "description": "T-shirts",
      |            "attributes":{
      |                "size": "M"
      |            }
      |        }
      |    ],
      |    "button_id": "btn-XXX",
      |    "campaign_id": "camp-XXX",
      |    "rate_card_id": "ratecard-XXX",
      |    "order_id": "order-1",
      |    "customer_order_id": "abcdef-123456",
      |    "account_id": "acc-XXX",
      |    "btn_ref": "srctok-XXYYZZ",
      |    "currency": "USD",
      |    "pub_ref": "publisher-token",
      |    "status": "validated",
      |    "event_date": "2017-01-01T20:00:00Z",
      |    "order_total": 6000,
      |    "advertising_id": "aaaaaaaa-1111-3333-4444-999999999999",
      |    "publisher_organization": "org-XXX",
      |    "commerce_organization": "org-XXX",
      |    "amount": 1000,
      |    "button_order_id": "btnorder-XXX",
      |    "publisher_customer_id": "10101",
      |    "id": "tx-XXX",
      |    "order_click_channel": "app",
      |    "category": "new-user-order",
      |    "validated_date": "2017-01-06T19:02:09Z"
      |  },
      |  "id": "hook-XXX",
      |  "event_type": "tx-validated"
      |}
    """.stripMargin

  val correctJsonRequest2 =
    """
      |{
      |  "request_id": "attempt-24dd807edb54c1e0",
      |  "data": {
      |    "posting_rule_id": null,
      |    "order_currency": "USD",
      |    "modified_date": "2018-12-19T21:01:31.325Z",
      |    "created_date": "2018-12-19T21:01:31.325Z",
      |    "order_line_items": [
      |      {
      |        "total": 2299,
      |        "identifier": "16pmxdak1kprjcxh8rzybw4a886bmf36mmyvcg3dp75w9wcjdjc0",
      |        "quantity": 1,
      |        "publisher_commission": 45,
      |        "gtin": "00711719516743",
      |        "subcategory1": "VIDEO GAMES BOOKS AND OTHER MEDIA",
      |        "subcategory2": "VIDEO GAMES",
      |        "attributes": {},
      |        "amount": 2299,
      |        "offer": null,
      |        "description": "20000:26000:26005:26169:26756:Playstation 4 Hori Licensed Mini Wired Gamepad (PS4)",
      |        "category": "ENTERTAINMENT"
      |      }
      |    ],
      |    "button_id": "static",
      |    "campaign_id": "camp-2dcfbefc4be8a0fc",
      |    "rate_card_id": "ratecard-5a3a18ee2a5fb386",
      |    "order_id": null,
      |    "account_id": "acc-79e483810bfa9b5f",
      |    "customer_order_id": null,
      |    "attribution_date": "2018-12-19T02:17:14Z",
      |    "btn_ref": "srctok-170fca9ba679a097",
      |    "currency": "USD",
      |    "pub_ref": null,
      |    "status": "pending",
      |    "event_date": "2018-12-19T21:01:25Z",
      |    "order_total": 2299,
      |    "advertising_id": null,
      |    "publisher_organization": "org-7537ad90e42d2ec0",
      |    "commerce_organization": "org-106cb4462581719b",
      |    "amount": 45,
      |    "button_order_id": "btnorder-7b13cb0d5985458b",
      |    "publisher_customer_id": "118627",
      |    "order_purchase_date": "2018-12-19T02:17:14Z",
      |    "id": "tx-16cb4920c135bd05",
      |    "order_click_channel": "app",
      |    "category": "new-user-order",
      |    "validated_date": null
      |  },
      |  "id": "hook-7bb17f0892e34817",
      |  "event_type": "tx-pending"
      |}
    """.stripMargin


  val zeroJsonRequest =
    """
      |{
      | "id": "hook-1fcc94214dad4fd8",
      | "event_type": "tx-pending",
      | "data": {
      |  "posting_rule_id": null,
      |  "order_currency": "USD",
      |  "modified_date": "2018-09-30T14:25:55.373Z",
      |  "created_date": "2018-09-30T14:25:55.373Z",
      |  "order_line_items": [
      |   {
      |    "description": "pandora-20",
      |    "amount": 0,
      |    "publisher_commission": 0,
      |    "attributes": {},
      |    "identifier": "pandora-20",
      |    "quantity": 1
      |   }
      |  ],
      |  "button_id": "static",
      |  "campaign_id": "camp-3d166ea17554b329",
      |  "rate_card_id": "ratecard-7c3f7827051dffee",
      |  "order_id": null,
      |  "account_id": "acc-79e483810bfa9b5f",
      |  "customer_order_id": null,
      |  "btn_ref": "srctok-54e36800c7ad4fb5",
      |  "currency": "USD",
      |  "pub_ref": null,
      |  "status": "pending",
      |  "event_date": "2018-09-30T14:25:49Z",
      |  "order_total": 0,
      |  "advertising_id": null,
      |  "publisher_organization": "org-7537ad90e42d2ec0",
      |  "commerce_organization": "org-46cb47cf8637e3d6",
      |  "amount": 0,
      |  "button_order_id": "btnorder-029eec837a403963",
      |  "publisher_customer_id": "172761",
      |  "order_purchase_date": "2018-09-30T13:00:00Z",
      |  "id": "tx-1e8c66b2e3478e0b",
      |  "order_click_channel": "webview",
      |  "category": "new-user-order",
      |  "validated_date": null
      | },
      | "request_id": "attempt-3caed95bc72850fb"
      |}
    """.stripMargin

  val incorrectJsonRequest =
    """
      |{
      |  "request_id": "attempt-XXX",
      |  "id": "hook-XXX",
      |  "event_type": "tx-validated"
      |}
    """.stripMargin
}