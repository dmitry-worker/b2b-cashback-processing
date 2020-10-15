package com.noproject.config

import cron4s.CronExpr

case class TxnDeliveryConfig(schedule: CronExpr)