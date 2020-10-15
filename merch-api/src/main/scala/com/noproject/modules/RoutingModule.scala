package com.noproject.modules

import com.google.inject.AbstractModule
import com.noproject.common.config.EnvironmentMode
import com.noproject.common.controller.route.{HealthCheckRoute, Routing}
import com.noproject.controller.route._
import com.noproject.controller.route.admin.{AdminCustomerRoute, TxnRefreshRoute}
import com.noproject.controller.route.admin.crap.CrapRoute
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}

class RoutingModule(env: EnvironmentMode) extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    val multiBinding = ScalaMultibinder.newSetBinder[Routing](binder)
    multiBinding.addBinding.to[HealthCheckRoute]
    multiBinding.addBinding.to[OfferRoute]
    multiBinding.addBinding.to[AuthRoute]
    multiBinding.addBinding.to[TransactionRoute]
    multiBinding.addBinding.to[DashboardRoute]
    multiBinding.addBinding.to[NetworkRoute]
    multiBinding.addBinding.to[CategoriesRoute]
    multiBinding.addBinding.to[TrackingRoute]
    multiBinding.addBinding.to[AdminCustomerRoute]
    multiBinding.addBinding.to[TxnRefreshRoute]

    if (env != EnvironmentMode.Prod) {
      multiBinding.addBinding.to[CrapRoute]
    }
  }
}
