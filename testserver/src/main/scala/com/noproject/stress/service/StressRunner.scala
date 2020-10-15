package com.noproject.stress.service

import cats.effect.{IO, Timer}
import cats.implicits._
import com.noproject.common.codec.json.JsonEncoded
import com.noproject.common.domain.service.CashbackTransactionDataService
import com.noproject.common.logging.DefaultLogging
import com.noproject.stress.config.StressTestPartnerConfig
import com.noproject.stress.data.gen.StreamingGenerator
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.{Status, Uri}

import scala.concurrent.duration._

class StressRunner[A <: JsonEncoded](
  suiteId: String
, client: Client[IO]
, gen: StreamingGenerator[A]
, stpc: StressTestPartnerConfig
, ctDS: CashbackTransactionDataService
)(implicit timer: Timer[IO]) extends DefaultLogging {

  val MAX_ATTEMPTS = 3
  val (uri, totalCount, totalAmount) = StressTestPartnerConfig.unapply(stpc).get

  def drainWithIO: IO[Unit] = {

    def submitRequest(txn: JsonEncoded): IO[Status] = {
      for {
        req <- POST(txn.jsonEncoded.noSpaces, Uri.unsafeFromString(this.uri))
        res <- client.status(req).recoverWith {
          case th =>
            logger.warn(s"Failed to post req: ${th}. Retrying")
            client.status(req)
        }
      } yield res
    }

    gen.genStream
      .evalMap(submitRequest)
      .compile.drain
  }

  def poll: IO[Unit] = {
    val stream = Stream.unfoldEval[IO, (Int, Int), Long]( (0, 0) ) {
      case (p, a) if a >= MAX_ATTEMPTS  =>
        // TODO: max attempts reached. Stopping.
        logger.info(s"Max attempts reached. Stopping having ${p} done")
        None.pure[IO]
      case (p, _) if p == totalCount   =>
        // all done! shutting down
        logger.info(s"All done. Stopping with success")
        None.pure[IO]
      case (p, a) =>
        logger.info(s"Continuing ")
        // should check current persist count
        ctDS.checkCountForDescription(suiteId).map { newPers =>
          val newAttempts = if (newPers == p) a + 1 else a
          Some( (0L -> (newPers, newAttempts) ) )
        }
    }.metered(5 seconds)
    stream.compile.drain
  }

}
