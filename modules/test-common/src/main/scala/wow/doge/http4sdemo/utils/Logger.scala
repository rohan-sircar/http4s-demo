package wow.doge.http4sdemo.utils

import io.odin.meta.Render
import io.odin.meta.Position
import monix.bio.UIO
import monix.bio.Task
import cats.effect.concurrent.Ref
import io.odin.syntax._
import monix.bio.IO
import java.util.concurrent.TimeUnit
import java.io.PrintStream
import cats.kernel.Order
import io.odin.formatter.Formatter
import io.odin.Level
import io.odin.LoggerMessage
import io.odin.loggers.DefaultLogger
import io.odin.loggers.ConsoleLogger
import cats.data.Chain
import io.odin.Logger

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
