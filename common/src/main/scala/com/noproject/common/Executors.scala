package com.noproject.common

import java.util.concurrent.{ArrayBlockingQueue, ThreadFactory, ThreadPoolExecutor, TimeUnit}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Executors {

  private val processors = Runtime.getRuntime.availableProcessors()

  val miscExec: ExecutionContextExecutor = executor(2, processors + 1, 20000, "misc")
  val partners: ExecutionContextExecutor = executor(2, 2, 20000, "partners")
  val dbExec: ExecutionContextExecutor = executor(2, processors + 1, 20000, "db")
  //  val akkaExec: ExecutionContextExecutor = executor(2, processors + 1, 20000, "akka")

  private def executor(poolSize: Int, maxPoolSize: Int, capacity: Int, name: String, keepAliveInSec: Int = 60) = {
    val queue = new ArrayBlockingQueue[Runnable](capacity)
    val executor = new ThreadPoolExecutor(poolSize, maxPoolSize, keepAliveInSec, TimeUnit.SECONDS,
      queue, ExecutorThreadFactory(name))
    ExecutionContext.fromExecutor(executor)
  }
}

case class ExecutorThreadFactory(name: String) extends ThreadFactory {
  override def newThread(r: Runnable): Thread =
    new Thread(r, name)
}
