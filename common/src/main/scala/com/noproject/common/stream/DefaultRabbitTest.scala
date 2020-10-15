package com.noproject.common.stream

import com.noproject.common.TestIO
import cats.effect.{ContextShift, IO, Resource, Timer}
import com.noproject.common.stream.impl.ImmediateRabbitProducer
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{AMQPChannel, ExchangeName, ExchangeType, QueueName, RoutingKey}
import io.circe.{Decoder, Encoder}


trait DefaultRabbitTest {

  protected def initRabbitBridge(
    exch: String
  , queue: String
  , routing: String
  , fs2r: Fs2Rabbit[IO]
  ): IO[Unit] = {
    fs2r.createConnectionChannel.use { ch =>
      for {
        _  <- fs2r.declareQueue(DeclarationQueueConfig.default( QueueName(queue) ))(ch)
        _  <- fs2r.declareExchange(ExchangeName(exch), ExchangeType.Topic)(ch)
        _  <- fs2r.bindQueue(QueueName(queue), ExchangeName(exch), RoutingKey(routing))(ch)
      } yield ()
    }
  }

  protected def immediateRabbitBridge[T](
    exch: String
  , queue: String
  , routing: String
  , fs2r: Fs2Rabbit[IO]
  )(implicit enc: Encoder[T], dec: Decoder[T], cs: ContextShift[IO], timer: Timer[IO]): Resource[IO, (RabbitProducer[T], RabbitConsuming[T])] = {
    initRabbitBridge(exch, queue, routing, fs2r).unsafeRunSync()
    for {
      rp <- ImmediateRabbitProducer.getInstance[T](exch, fs2r)
      rc <- RabbitConsuming.getInstance[T](queue, fs2r)
    } yield (rp, rc)
  }

}
