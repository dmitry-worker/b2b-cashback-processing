package com.noproject.common.domain.model.merchant.mapping

case class SynchronizationInfo(
                                created:    Int
                                , updated:    Int
                                , deleted:    Int
                                , unchanged:  Int
                              ) {
  override def toString: String = {
    s"synchronization done. C: $created U: $updated D: $deleted N: $unchanged"
  }
}

case class MerchantMappings(
                             nameMappings: Map[String, String]
                             , categoryMappings: Map[String, Seq[String]]
                           ) {

  private lazy val allowedCategories: Set[String] = {
    categoryMappings.values.flatten.toSet
  }

  def remapCategories(existing: Iterable[String]): List[String] = {
    val res = existing.flatMap { cat =>
      if (allowedCategories.contains(cat)) Seq(cat)
      else categoryMappings.getOrElse(cat, Nil)
    }
    res.toList.distinct
  }

  def remapName(name: String): String = {
    nameMappings.getOrElse(name, name)
  }

}

object MerchantMappings {

  val empty = MerchantMappings(Map(), Map())

}