package com.noproject.controller.dto.dashboard

import java.time.Instant

import com.noproject.common.domain.model.transaction.CashbackTxnStatus

case class DashboardStatsRequest(
                                  merchant: Option[String] = None
                                , beginsAt: Option[Instant] = None
                                , endsAt:   Option[Instant] = None
                                , tags:     Option[List[String]] = None
                                , status:   Option[CashbackTxnStatus] = None
)
