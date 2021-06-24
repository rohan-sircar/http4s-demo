package wow.doge.http4sdemo.server.utils

import cats.syntax.show._
import io.circe.Encoder
import io.circe.syntax._
import io.odin.Level
import io.odin.Level.Debug
import io.odin.Level.Info
import io.odin.Level.Trace
import io.odin.Level.Warn
import io.odin.LoggerMessage
import io.odin.formatter.Formatter
import io.odin.formatter.Formatter._
import io.odin.formatter.options.PositionFormat
import io.odin.formatter.options.ThrowableFormat

object JsonFormatter {

  val formatter: Formatter =
    create(ThrowableFormat.Default, PositionFormat.Full)

  def create(throwableFormat: ThrowableFormat): Formatter =
    create(throwableFormat, PositionFormat.Full)

  def create(
      throwableFormat: ThrowableFormat,
      positionFormat: PositionFormat
  ): Formatter = {
    implicit val encoder: Encoder[LoggerMessage] =
      loggerMessageEncoder(throwableFormat, positionFormat)
    (msg: LoggerMessage) => msg.asJson.noSpaces
  }
  def loggerMessageEncoder(
      throwableFormat: ThrowableFormat,
      positionFormat: PositionFormat
  ): Encoder[LoggerMessage] =
    Encoder.forProduct7(
      "level",
      "log_message",
      "context",
      "exception",
      "position",
      "thread_name",
      "timestamp"
    )(m =>
      (
        m.level.show,
        m.message.value,
        m.context,
        m.exception.map(t => formatThrowable(t, throwableFormat)),
        formatPosition(m.position, positionFormat),
        m.threadName,
        formatTimestamp(m.timestamp)
      )
    )
}
object PaddedFormatter {
  val default =
    apply(ThrowableFormat.Default, PositionFormat.Full, true)

  import Formatter._
  def apply(
      throwableFormat: ThrowableFormat,
      positionFormat: PositionFormat,
      printCtx: Boolean
  ): Formatter = { (msg: LoggerMessage) =>
    val ctx =
      if (printCtx) fansi.Color.Magenta(formatCtx(msg.context))
      else fansi.Str("")
    val timestamp = f"${formatTimestamp(msg.timestamp)}%-23s"
    val threadName =
      // fansi.Str.join(
      //   fansi.Str("["),
      fansi.Color.LightGreen(f"${"[" + msg.threadName + "]"}%-25s")
    //   fansi.Str("]")
    // )
    val m = f"${msg.level.show}%-5s"
    val level = msg.level match {
      case Debug       => fansi.Color.DarkGray(m)
      case Warn        => fansi.Color.True(255, 99, 71)(m)
      case Level.Error => fansi.Color.Red(m)
      case Info        => fansi.Color.Blue(m)
      case Trace       => fansi.Color.True(255, 215, 0)(m)
    }
    val position =
      fansi.Color.LightBlue(
        f"${formatPosition(msg.position, positionFormat)}%-35s"
      )

    val throwable = msg.exception match {
      case Some(t) =>
        fansi.Color.Red(
          s"${System.lineSeparator()}${formatThrowable(t, throwableFormat)}"
        )
      case None =>
        fansi.Str("")
    }

    // f"$time | $logLevel%-5s | $classname | $msg\n"
    // pprint.log _
    //   p"$timestamp [$threadName] $level $position - ${msg.message.value}$ctx$throwable"
    fansi.Str
      .join(
        timestamp,
        " | ",
        threadName,
        " | ",
        level,
        " | ",
        position,
        ctx,
        s" | ${msg.message.value}",
        throwable
      )
      .render
  }

}
