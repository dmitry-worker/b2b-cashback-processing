package com.noproject.service

import java.time.Instant

import com.noproject.common.domain.model.customer.{Consumer, PlainTrackingParams}
import com.noproject.common.security.Hash

object TrackingOps {

  def calculateTrackingHash(user: Consumer, offerId: String): String = {
    val now = Instant.now.toEpochMilli.toString
    val tp = PlainTrackingParams(user.hash, offerId, now)
    Hash.base64.encode(tp.toString)
  }

}
