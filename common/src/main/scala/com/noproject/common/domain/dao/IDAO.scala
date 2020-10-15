package com.noproject.common.domain.dao

import cats.Reducible
import cats.effect.IO
import cats.implicits._
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.model.{GinWrapper, Location, Money, Percent}
import doobie.free.connection.ConnectionIO
import doobie.util.Put
import doobie.util.query.Query
import enumeratum.{Enum, EnumEntry}
import io.chrisdavenport.fuuid.FUUID
import io.circe.Json
import io.circe.parser.parse
import org.postgis.{PGgeometry, Point}
import org.postgresql.util.PGobject
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import io.chrisdavenport.fuuid.doobie.implicits._
import shapeless.HList

import scala.language.implicitConversions


trait IDAO {

  type T

  def tableName: String
  def keyFieldList: List[String]
  def allFieldList: List[String]
  def immFieldList: List[String] = List()
  def ginField: String = ""

  private lazy val keyFieldSet = keyFieldList.toSet
  private lazy val immFieldSet = immFieldList.toSet

  final lazy val valFieldList = allFieldList.filterNot(f => (keyFieldSet contains f) || (immFieldSet contains f))

  final lazy val keyHoles = keyFieldList.map(_ => "?").mkString(", ")
  final lazy val valHoles = valFieldList.map(_ => "?").mkString(", ")
  final lazy val allHoles = allFieldList.map(_ => "?").mkString(", ")
  final lazy val allHolesWithGin = allHoles + ", to_tsvector('english', ?)"

  final lazy val keyFields = keyFieldList.mkString(", ")
  final lazy val valFields = valFieldList.mkString(", ")
  final lazy val allFields = allFieldList.mkString(", ")
  final lazy val allFieldsWithGin = (allFieldList :+ ginField).mkString(", ")

  protected def sp: DefaultPersistence

  implicit def transaction[T](cio: ConnectionIO[T]): IO[T] = {
    cio.transact(sp.xar)
  }

  implicit val jsonMeta: Meta[Json] = {
    import com.noproject.common.PipeOps._
    Meta.Advanced.other[PGobject]("json").timap[Json](
      a => parse(a.getValue).leftMap[Json](e => throw e).merge)(
      a => new PGobject <| (_.setType("json")) <| (_.setValue(a.noSpaces))
    )
  }

  implicit val moneyMeta: Meta[Money] = {
    Meta.BigDecimalMeta.imap(bd => Money(bd))(m => m.amount.underlying())
  }

  implicit val percentMeta: Meta[Percent] = {
    Meta.BigDecimalMeta.imap(bd => Percent(bd))(m => m.amount.underlying())
  }

  implicit val uuidMeta: Meta[FUUID] = FuuidType

  implicit val PGpointMeta: Meta[Location] = {
    import com.noproject.common.PipeOps._
    Meta.Advanced.other[PGgeometry]("geometry").timap[Location](
      g => Location.from(g.getGeometry.asInstanceOf[Point])
    )(
      l => new PGgeometry(l.toPoint <| (_.setSrid(4326)))
    )
  }

  implicit def enumMeta[T <: EnumEntry](implicit t: Enum[T]): Meta[T] = {
    Meta.StringMeta.imap(t.withName)(_.entryName)
  }

  protected def insert0(values: List[T])(implicit w:Write[T]): ConnectionIO[Int] = {
    val sql = s"insert into $tableName ($allFields) values ($allHoles)"
    Update[T](sql).updateMany(values)
  }

  protected def insert1(values: List[T])(implicit w:Write[T]): ConnectionIO[Int] = {
    val sql = s"insert into $tableName ($allFields) values ($allHoles) on conflict do nothing"
    Update[T](sql).updateMany(values)
  }

  protected def insertGin0[H <: HList](values: List[H])(implicit w:Write[H]): ConnectionIO[Int] = {
    val sql = s"insert into $tableName ($allFieldsWithGin) values ($allHolesWithGin)"
    Update[H](sql).updateMany(values)
  }

  protected def insertGin1[H <: HList](values: List[H])(implicit w:Write[H]): ConnectionIO[Int] = {
    val sql = s"insert into $tableName ($allFieldsWithGin) values ($allHolesWithGin) on conflict do nothing"
    Update[H](sql).updateMany(values)
  }

  protected def upsertGin[H <: HList](values: List[H], conflictFields: List[String], updateFields: List[String])(implicit w:Write[H]): ConnectionIO[Int] = {
    val conflictFieldsSql = conflictFields.mkString(",")
    val updateFieldsSql = updateFields.map(f => s"$f = excluded.$f").mkString(",")
    val sql =
      s"""
        |insert into $tableName ($allFieldsWithGin) values ($allHolesWithGin)
        |on conflict ($conflictFieldsSql) do update
        |set $updateFieldsSql
      """.stripMargin
    Update[H](sql).updateMany(values)
  }

  protected def findAll0(implicit r:Read[T]): ConnectionIO[List[T]] = {
    val sql = s"select $allFields from $tableName"
    Query[Unit, T](sql).toQuery0(()).to[List]
  }

  protected def selectAll(prefix: Option[String] = None): Fragment = {
    val colPrefix = prefix.map( p => s"$p." ).getOrElse( "" )
    val fields = allFieldList.map( n => colPrefix + n ).mkString( ", " )

    val table = prefix.map( p => s"$tableName $p" ).getOrElse( tableName )

    Fragment.const( s"SELECT $fields FROM $table" )
  }

  def deleteAll(): IO[Int] = deleteAllTxn()

  def deleteAllTxn(): ConnectionIO[Int] = {
    val sql = s"delete from $tableName"
    Update[Unit](sql).toUpdate0(()).run
  }

  def arrayContains[F[_]: Reducible, A: Put](f: Fragment, fs: F[A], pgTypeCast: String): Fragment =
    fs.toList.map(a => fr0"$a").foldSmash1(f ++ fr0"@> ARRAY[", fr",", fr"]") ++ Fragment.const(s"::$pgTypeCast[]")

  def arrayContainsNot[F[_]: Reducible, A: Put](f: Fragment, fs: F[A], pgTypeCast: String): Fragment =
    Fragment.const("NOT") ++ fs.toList.map(a => fr0"$a").foldSmash1(f ++ fr0"@> ARRAY[", fr",", fr"]") ++ Fragment.const(s"::$pgTypeCast[]")

}
