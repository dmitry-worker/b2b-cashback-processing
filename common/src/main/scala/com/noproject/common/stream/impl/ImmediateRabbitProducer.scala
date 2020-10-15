package com.noproject.common.stream.impl

import java.time.Instant

import cats.data.Kleisli
import cats.effect.{ContextShift, IO, Resource, Timer}
import com.noproject.common.stream.impl.ImmediateRabbitProducer.EnvelopeProducer
import com.noproject.common.stream.{RabbitProducer, DefaultRabbitEnvelope}
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model.{AMQPChannel, AmqpMessage, ExchangeName, RoutingKey}
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._



class ImmediateRabbitProducer[T](pub: EnvelopeProducer[T]) extends RabbitProducer[T] {

  override def submit(rk: String, messages: List[T]): IO[Unit] = {
    pub.apply(RoutingKey(rk))(DefaultRabbitEnvelope(messages.size, Instant.now(), Some(rk), messages))
  }

}


object ImmediateRabbitProducer {

  type EnvelopeProducer[T] = RoutingKey => DefaultRabbitEnvelope[T] => IO[Unit]

  def getInstance[T: Encoder](exchange: String, rabbit: Fs2Rabbit[IO]): Resource[IO, ImmediateRabbitProducer[T]] = {

    implicit def envelopeEncoder: MessageEncoder[IO, DefaultRabbitEnvelope[T]] = {
      implicit val encoder: Encoder[DefaultRabbitEnvelope[T]] = deriveEncoder[DefaultRabbitEnvelope[T]]
      val k: Kleisli[IO, DefaultRabbitEnvelope[T], String] = Kleisli(renv => IO.delay(renv.asJson.spaces2))
      val f: Kleisli[IO, String, AmqpMessage[Array[Byte]]] = AmqpMessage.stringEncoder[IO]
      f compose k
    }

    rabbit.createConnectionChannel.flatMap { implicit channel =>
      val acquire = rabbit.createRoutingPublisher[DefaultRabbitEnvelope[T]](ExchangeName(exchange)).map { pub =>
        new ImmediateRabbitProducer[T](pub)
      }

      Resource.liftF[IO, ImmediateRabbitProducer[T]](acquire)
    }

  }

}
