package com.noproject.common.stream.impl

import cats.effect.IO
import com.noproject.common.domain.dao.customer.ConsumerDAO
import com.noproject.common.domain.model.customer.Consumer
import com.noproject.common.logging.DefaultLogging
import com.noproject.common.stream.GroupingQueueResource
import javax.inject.{Inject, Singleton}


@Singleton
class ConsumerQueue @Inject()(
  dao: ConsumerDAO
, gq: GroupingQueueResource[Consumer]
) extends DefaultLogging {

  def put(consumer: Consumer): IO[Unit] = {
    gq.put(consumer)
  }

  def runForever:IO[Unit] = fs2.Stream
    .unfoldEval(gq.output)(mv => mv.take.map(c => Option((c, mv))))
    .evalMap(analyzeFraud)
    .evalMap(commitWrite)
    .compile.drain

  private def analyzeFraud(consumers: List[Consumer]): IO[List[Consumer]] = {
    // TODO: check if there's 10% / 25% / 50% of same customer in list
    // TODO: this might be fraud
    IO.pure(consumers)
  }

  private def commitWrite(consumers: List[Consumer]): IO[Int] = {
    dao.insert(consumers)
  }

}
