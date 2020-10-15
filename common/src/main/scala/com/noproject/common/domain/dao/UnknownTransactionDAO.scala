package com.noproject.common.domain.dao

import com.noproject.common.domain.DefaultPersistence
import javax.inject.{Inject, Singleton}

@Singleton
class UnknownTransactionDAO @Inject()(sp: DefaultPersistence) extends TransactionDAO(sp, "unknown_transaction")