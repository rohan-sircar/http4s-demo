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
import cats.data.Chain

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
  ): Task[Unit] =
    chain.update(c => c :+ formatter.format(msg))

  override def log(msg: LoggerMessage): Task[Unit] = write(msg, formatter)
}

final class TracingStubLoggerReverse(
    stack: Ref[Task, List[String]],
    formatter: Formatter = Formatter.colorful,
    override val minLevel: Level
) extends DefaultLogger[Task](minLevel) {

  private def write(
      msg: LoggerMessage,
      formatter: Formatter
  ): Task[Unit] =
    stack.update(lst => formatter.format(msg) :: lst)

  override def log(msg: LoggerMessage): Task[Unit] = write(msg, formatter)
}
