package com.noproject.testserver

import java.time.Instant
import java.time.format.DateTimeFormatter

import cats.effect.{Concurrent, IO}
import cats.effect.concurrent.{MVar, Ref}
import io.circe.Json


sealed case class QueryLogItem(moment: Instant, request: Json)

case class QueryLog(private val log: Ref[IO, List[QueryLogItem]]) {
  private val MAX_SIZE = 20

  def get: IO[List[QueryLogItem]] = {
    log.get
  }

  def post(moment: Instant, request: Json): IO[Unit] = {
    def truncate(log: List[QueryLogItem]): List[QueryLogItem] = {
      if (log.size > MAX_SIZE) log.takeRight(MAX_SIZE) else log
    }

    for {
      list    <- log.get
      newList  = truncate(list :+ QueryLogItem(moment, request))
      _       <- log.update(_ => newList)
    } yield ()
  }
}

object QueryLogBuilder {
  def build(implicit c: Concurrent[IO]): IO[QueryLog] = {
    val log = Ref.of[IO, List[QueryLogItem]](List.empty)
    log.map(l => QueryLog(l))
  }
}

