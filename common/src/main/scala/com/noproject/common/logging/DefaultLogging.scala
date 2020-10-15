package com.noproject.common.logging

import org.log4s.getLogger
import org.slf4j.{Marker, MarkerFactory}


/**
  Use this trait instead of LazyLogging to work with log4s instead of sl4j
  It allows to use DefaultMarker objects
 */
trait DefaultLogging {
  @transient
  protected lazy val logger: org.log4s.Logger = getLogger(getClass)
}

/**
  This object's CommonMarkerLogger class allows to use CommonMarker objects in RhoRoutes subclasses
  */
object DefaultLogging {

  abstract class DefaultLoggingMarker(val name: String)
  class CustomizableDefaultLoggingMarker(key: String, value: String) extends DefaultLoggingMarker(name = s"[$key#$value]")
  case class CustomerMarker(customerId: String) extends CustomizableDefaultLoggingMarker("customer", customerId)
  case class UserMarker(userId: String) extends CustomizableDefaultLoggingMarker("user", userId)
  case class OfferMarker(userId: String) extends CustomizableDefaultLoggingMarker("offer", userId)

  implicit class DefaultMarkerLogger(logger: org.log4s.Logger) {
    private def mrk[T <: DefaultLoggingMarker](markers: List[T]): Marker = {
      MarkerFactory.getMarker(markers
        .sortBy(_.getClass.getName)
        .map(_.name)
        .mkString
      )
    }

    def debug[T <: DefaultLoggingMarker](message: String, markers: T*): Unit = logger.logger.debug(mrk(markers.toList), message)
    def info[T <: DefaultLoggingMarker](message: String, markers: T*): Unit = logger.logger.info(mrk(markers.toList), message)
    def warn[T <: DefaultLoggingMarker](message: String, markers: T*): Unit = logger.logger.warn(mrk(markers.toList), message)
    def error[T <: DefaultLoggingMarker](message: String, markers: T*): Unit = logger.logger.error(mrk(markers.toList), message)
  }
}
