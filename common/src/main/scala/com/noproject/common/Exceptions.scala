package com.noproject.common

import com.noproject.common.domain.model.eventlog.Loggable

object Exceptions {
  case class ConfigException(key: Option[String]) extends Throwable {
    override def getMessage: String = {
      key.fold(s"Cannot create config")(k => s"Bad config for key $k: ${super.getMessage}")
    }
  }

  // NotFoundException will be handled on the router and server will response 404 to client
  trait NotFoundException extends Throwable

  // universal NotFoundException implementation
  case class ObjectNotFoundException(message: Option[String]) extends NotFoundException {
    override def getMessage: String = message.getOrElse(super.getMessage)
  }


  // some special NotFoundException implementations with predefined error messages
  case class SessionNotFoundException(token: String) extends NotFoundException {
    override def getMessage: String = s"Session $token not found"
  }

  case class OfferNotFoundException(id: String) extends NotFoundException {
    override def getMessage: String = s"Offer $id not found"
  }

  case class TransactionNotFoundException(id: String) extends NotFoundException {
    override def getMessage: String = s"Cashback transaction $id not found"
  }

  case class CustomerNotFoundException(apiKey: String) extends NotFoundException {
    override def getMessage: String = s"Customer with id $apiKey not found"
  }

  case class UserNotFoundExceiption(hash: String) extends NotFoundException {
    override def getMessage: String = s"User with hash $hash not found"
  }


  // class for handling errors in transaction processing flow
  case class LoggableException[T <: Loggable](failureObject: T
                                            , message: String
                                            , details: Option[String] = None) extends Throwable {
    override def getMessage: String = message + details.map(m => s" Details is: $m").getOrElse("")

  }

  /**
    * Throwable indicating a condition which should never happen.
    */
  case class ProgrammerError( explain: String ) extends Error
}
