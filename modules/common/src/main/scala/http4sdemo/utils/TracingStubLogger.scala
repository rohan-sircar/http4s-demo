package wow.doge.http4sdemo.utils

import cats.data.Chain
import cats.effect.concurrent.Ref
import io.odin.Level
import io.odin.Logger
import io.odin.LoggerMessage
import io.odin.formatter.Formatter
import io.odin.loggers.DefaultLogger
import monix.bio.Task

/** A logger that only records log events in the given stack,
  * but doesn't actually print anything
  */
final class TracingStubLogger(
    chain: Ref[Task, Chain[String]],
    formatter: Formatter = Formatter.colorful,
    override val minLevel: Level
) extends DefaultLogger[Task](minLevel) {

  private def write(
      msg: LoggerMessage,
      formatter: Formatter
  ): Task[Unit] = chain.update(_ :+ formatter.format(msg))

  def submit(msg: LoggerMessage): Task[Unit] = write(msg, formatter)

  def withMinimalLevel(level: Level): Logger[Task] =
    new TracingStubLogger(chain, formatter, level)

  def logs = chain.get
}

final class TracingStubLoggerReverse(
    stack: Ref[Task, List[String]],
    formatter: Formatter = Formatter.colorful,
    override val minLevel: Level
) extends DefaultLogger[Task](minLevel) {

  private def write(
      msg: LoggerMessage,
      formatter: Formatter
  ): Task[Unit] = stack.update(formatter.format(msg) :: _)

  def submit(msg: LoggerMessage): Task[Unit] = write(msg, formatter)

  def withMinimalLevel(level: Level): Logger[Task] =
    new TracingStubLoggerReverse(stack, formatter, level)
}
