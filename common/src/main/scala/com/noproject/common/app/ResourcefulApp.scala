package com.noproject.common.app

import cats.effect.{IO, Resource}
import com.noproject.common.domain.DbConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import org.http4s.metrics.prometheus.PrometheusExportService
import shapeless.HList

// base type that introduces abstract resources
// and the way they should be created
trait ResourcefulApp {

  protected type Resources <: HList

  protected def createResources(pes: PrometheusExportService[IO]): Resource[IO, Resources]

  protected def dbConfig: DbConfig

}
