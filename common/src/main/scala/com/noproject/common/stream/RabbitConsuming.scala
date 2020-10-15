package com.noproject.common.stream

import cats.implicits._
import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Fiber, IO, Resource, Timer}
import com.noproject.common.data.gen.RandomValueGenerator
import com.typesafe.scalalogging.LazyLogging
import dev.profunktor.fs2rabbit.arguments.SafeArg
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.json.Fs2JsonDecoder
import dev.profunktor.fs2rabbit.model.AckResult.{Ack, NAck}
import dev.profunktor.fs2rabbit.model.{AMQPChannel, AckResult, AmqpEnvelope, ConsumerArgs, ConsumerTag, DeliveryTag, QueueName}
import fs2.{Pipe, Stream}
import io.circe.{Decoder, Encoder, Error}
import io.circe.generic.semiauto._

class RabbitConsuming[T](
  private val channel: AMQPChannel
, private val fiber: Fiber[IO, Unit]
, val mvar: MVar[IO, Option[DefaultRabbitEnvelope[T]]]
) {

  def drainWith(fn: DefaultRabbitEnvelope[T] => Unit): IO[Unit] = {
    fs2.Stream
      .unfoldEval(mvar)(mv => mv.take.map(c => Option((c, mv))))
      .collect { case Some(data) => data }
      .map(fn)
      .compile.drain
  }

  def drainWithIO(fn: DefaultRabbitEnvelope[T] => IO[Unit]): IO[Unit] = {
    fs2.Stream
      .unfoldEval(mvar)(mv => mv.take.map(c => Option((c, mv))))
      .collect { case Some(data) => data }
      .evalMap(fn)
      .compile.drain
  }

}


object RabbitConsuming extends RandomValueGenerator with LazyLogging {

  private object Fs2RabbitDecoder extends Fs2JsonDecoder
  private val NOARGS = Map[String, SafeArg]()

  def getInstance[T: Decoder](
    queueName: String
  , rabbit: Fs2Rabbit[IO]
  )(implicit conc: ContextShift[IO], timer: Timer[IO]): Resource[IO, RabbitConsuming[T]] = {

    val TAG   = ConsumerTag(randomStringUUID)
    val args  = Some(ConsumerArgs(TAG, false, false, NOARGS))

    implicit val decoder: Decoder[DefaultRabbitEnvelope[T]] = deriveDecoder[DefaultRabbitEnvelope[T]]

    def dataStream(mvar: MVar[IO, Option[DefaultRabbitEnvelope[T]]]): Pipe[IO, AmqpEnvelope[String], AckResult] = { source =>
      source.evalMap { ae =>
        IO.delay {
          val (result, tag) = Fs2RabbitDecoder.jsonDecode[DefaultRabbitEnvelope[T]].apply(ae)
          (result, tag)
        }
      }.evalMap {
        case (Left(e), tag) =>
          IO.pure(NAck(tag))
        case (Right(data), tag) =>
          mvar.put(Some(data)).as(Ack(tag))
      }
    }

    rabbit.createConnectionChannel.flatMap { implicit channel =>
      val acquire: IO[RabbitConsuming[T]] = {
        for {
          mvar      <- MVar.empty[IO, Option[DefaultRabbitEnvelope[T]]]
          consTuple <- rabbit.createAckerConsumer(QueueName(queueName), consumerArgs = args)
          (af, str)  = consTuple
          fiber     <- str.through(dataStream(mvar)).evalMap(af).compile.drain.start
        } yield {
          new RabbitConsuming[T](channel, fiber, mvar)
        }
      }

      val release: RabbitConsuming[T] => IO[Unit] = {
        rcons => IO.suspend {
          for {
            // it closes the channel by itself on fiber cancelling
            signal    <- rcons.mvar.put(None)
            joined    <- rcons.fiber.cancel
          } yield {
            logger.warn("Consumer fiber joined as stream was closed")
          }
        }
      }

      Resource.make(acquire)(release)
    }

  }

}