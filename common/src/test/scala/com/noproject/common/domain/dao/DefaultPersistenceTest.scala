package com.noproject.common.domain.dao

import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.TestIO
import com.noproject.common.domain.DefaultPersistence
import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor
import org.scalatest.Assertions

import scala.concurrent.ExecutionContext

trait DefaultPersistenceTest extends IOChecker with Assertions {

  implicit val t: Timer[IO]         = TestIO.t
  implicit val cs: ContextShift[IO] = TestIO.cs

  lazy val transactor = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver"
  , "jdbc:postgresql://localhost:5432/test"
  , "postgres"
  , ""
  )

  lazy val xar: DefaultPersistence = DefaultPersistence(transactor)

  lazy val rollbackTransactor: Transactor[IO] = {
    Transactor.after.set(transactor, doobie.HC.rollback)
  }


}
