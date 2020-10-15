package com.noproject.common.domain.model.customer

import com.noproject.common.logging.DefaultLogging

case class PlainTrackingParams(
  user:  String = ""
, offer: String = ""
, time:  String = ""
) {
  override def toString: String = List(user, offer, time).mkString("|")
}

object PlainTrackingParams extends DefaultLogging {
  def apply(string: String): PlainTrackingParams = {
    val list = string.split('|')
    if (list.length != 3) {
      logger.warn(s"Can't parse tracking hash $string")
      PlainTrackingParams()
    } else {
      PlainTrackingParams(list(0), list(1), list(2))
    }
  }
}