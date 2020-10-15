package com.noproject.common.domain.model

import scala.util.Try

case class CashbackUserId(
  customerName:  String
, userId:        String
)

object CashbackUserId {

  def unapply(arg: String): Option[CashbackUserId] = arg.indexOf("::") match {
    case x if x > 0 && x < arg.length - 2 =>
      val parsed = Try {
        val pid = arg.take(x)
        val uid = arg.drop(x + 2)
        require(uid.forall(_.isLetterOrDigit))
        CashbackUserId(pid, uid)
      }
      parsed.toOption
    case _ =>
      None
  }

}
