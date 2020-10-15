package com.noproject.common.config

import java.time.Clock

import cats.effect.{ContextShift, IO, Timer}
import com.noproject.common.cache.SimpleCache
import com.noproject.common.codec.json.ElementaryCodecs
import com.noproject.common.domain.dao.ConfigDAO
import com.noproject.common.logging.DefaultLogging
import com.typesafe.config.Config
import io.circe.config.syntax._
import io.circe.{ACursor, Decoder}

import scala.concurrent.duration._



trait ConfigProvider[A] extends DefaultLogging with ElementaryCodecs {
  def getConfig: IO[A] = load
  protected def load: IO[A]
  protected def fail(err: Throwable): IO[A]
}



trait CachedConfigProvider[A] extends ConfigProvider[A] {
  protected implicit val T: Timer[IO]
  protected implicit val C: ContextShift[IO]
  protected val clock: Clock
  private val cache = SimpleCache
    .apply[A](300, load)
    .unsafeRunTimed(1 seconds)
    .get

  override def getConfig: IO[A] = cache.demand

}



trait DBConfigProvider[A] extends ConfigProvider[A] {
  protected val configPath: String
  protected val configDAO:  ConfigDAO
  protected implicit val decoder: Decoder[A]
  protected lazy val pathElements = configPath.split("\\.")
  override protected def load: IO[A] = configDAO.findByKey(pathElements.head).flatMap {
    case Some(conf) =>
      val cursor:ACursor = conf.value.hcursor
      val obj = pathElements.tail.foldLeft(cursor) { _ downField _ }
      obj.as[A] match {
        case Right(value) => IO.pure(value)
        case Left(error)  => IO.raiseError(new RuntimeException(error.toString(), error.getCause))
      }
    case _ =>
      fail(new RuntimeException(s"Path ${configPath} is not found in database..."))
  }
}



trait FileConfigProvider[A] extends ConfigProvider[A] {
  protected implicit def decoder: Decoder[A]
  protected def configPath: String
  protected def applicationConf: Config

  // it is pointless to re-read configuration files in runtime.
  val value = load.unsafeRunSync()
  override final def getConfig: IO[A] = IO.pure(value)
  override protected def load: IO[A] = {
    IO.delay(applicationConf.as[A](configPath)).flatMap {
      case Left(err) => fail(err)
      case Right(a)  => IO.pure(a)
    }
  }
}



trait FallbackConfigProvider[A] extends ConfigProvider[A] {
  def fallback: ConfigProvider[A]
  override protected def fail(err: Throwable): IO[A] = {
    logger.warn(s"$this Failed to grab config: $err")
    logger.warn(s"Will recover with ${fallback}")
    fallback.getConfig
  }
}



trait FailFastConfigProvider[A] extends ConfigProvider[A] {
  override protected def fail(err: Throwable): IO[Nothing] = {
    IO.raiseError(err)
  }
}




class DefaultFileConfigProvider[A](
  protected val configPath: String
, protected val applicationConf: Config
)(implicit protected val decoder: Decoder[A])
  extends FileConfigProvider[A]
    with FailFastConfigProvider[A]

/** example! */
class CachedDBConfigProvider[A](
  val configPath: String
, val configDAO:  ConfigDAO
, val clock:      Clock
)(
  implicit
  val T: Timer[IO]
, val C: ContextShift[IO]
, val decoder: Decoder[A]
) extends DBConfigProvider[A] with CachedConfigProvider[A] with FailFastConfigProvider[A]