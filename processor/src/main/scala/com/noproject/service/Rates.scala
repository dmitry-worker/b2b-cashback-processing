package com.noproject.service

import java.time.Instant


case class Rates(timestamp: Long, rates: Map[String, BigDecimal])