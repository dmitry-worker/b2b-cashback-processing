package com.noproject.service.auth

import com.noproject.common.config.ConfigProvider
import com.noproject.common.domain.model.customer.AccessRole
import com.noproject.config.AuthConfig
import com.noproject.domain.service.customer.SessionDataService
import javax.inject.Inject

class AuthenticatorUser @Inject()(
  authCP: ConfigProvider[AuthConfig]
, sessionDS:  SessionDataService
) extends AuthenticatorJWT(authCP, sessionDS) {

  override protected val accessRole: AccessRole = AccessRole.Customer

}
