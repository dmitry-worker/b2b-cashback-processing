package com.noproject.domain.model.customer

import java.time.Instant

import com.noproject.common.domain.model.customer.AccessRole

case class CustomerSession( session: Session, customerRoles: Set[AccessRole] ) {
  val customerName: Option[String] = session.customerName

  def notExpired(moment: Instant): Boolean = {
    session.expiredAt.isAfter(moment)
  }

}

object CustomerSession {
  def anonymous= {
    CustomerSession( Session.anonymous, Set( AccessRole.Customer ) )
  }

  def apply( session: Session, customerRoles: Set[AccessRole] ) =
    new CustomerSession( session, customerRoles )

  def apply( sessionId: Long, customerName: Option[String], sessionToken: String, startedAt: Instant, expiredAt: Instant, customerRoles: Set[ AccessRole ] ) : CustomerSession = {
    val session = Session( sessionId, customerName, sessionToken, startedAt, expiredAt )
    new CustomerSession( session, customerRoles )
  }

  def unapply( cs: CustomerSession ): Option[(Long, Option[String], String, Instant, Instant, Set[AccessRole] )] =
    Option( (cs.session.sessionId, cs.session.customerName, cs.session.sessionToken, cs.session.startedAt, cs.session.expiredAt, cs.customerRoles) )

}


