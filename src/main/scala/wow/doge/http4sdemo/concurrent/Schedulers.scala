package wow.doge.http4sdemo.schedulers

import cats.effect.Blocker
import com.typesafe.scalalogging.Logger
import monix.execution.Scheduler
import monix.execution.UncaughtExceptionReporter

final case class Schedulers(
    io: Schedulers.IoScheduler,
    async: Schedulers.AsyncScheduler
)

object Schedulers {
  val reporter = UncaughtExceptionReporter { ex =>
    val logger = Logger[Schedulers]
    logger.error("Uncaught exception", ex)
  }

  val default = Schedulers(
    IoScheduler(
      Scheduler
        .io()
        .withUncaughtExceptionReporter(Schedulers.reporter)
    ),
    AsyncScheduler(
      Scheduler.global
        .withUncaughtExceptionReporter(Schedulers.reporter)
    )
  )

  final case class AsyncScheduler(value: Scheduler)
  final case class IoScheduler(value: Scheduler) {
    val blocker = Blocker.liftExecutionContext(value)
  }
}
