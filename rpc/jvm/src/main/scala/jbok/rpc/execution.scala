package jbok.rpc

object execution {
  import java.nio.channels.AsynchronousChannelGroup
  import java.util.concurrent.Executors

  import fs2.Scheduler
  import spinoco.fs2.http.util

  import scala.concurrent.ExecutionContext

  implicit val EC: ExecutionContext = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(8, util.mkThreadFactory("EC", daemon = true)))

  implicit val Sch: Scheduler = Scheduler.fromScheduledExecutorService(
    Executors.newScheduledThreadPool(4, util.mkThreadFactory("fs2-scheduler", daemon = true)))

  implicit val AG: AsynchronousChannelGroup = AsynchronousChannelGroup.withThreadPool(
    Executors.newCachedThreadPool(util.mkThreadFactory("AG", daemon = true)))
}
