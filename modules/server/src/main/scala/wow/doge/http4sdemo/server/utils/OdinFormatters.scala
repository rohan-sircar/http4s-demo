package wow.doge.http4sdemo.server.utils

import cats.syntax.show._
import io.odin.Level
import io.odin.Level.Debug
import io.odin.Level.Info
import io.odin.Level.Trace
import io.odin.Level.Warn
import io.odin.LoggerMessage
import io.odin.formatter.Formatter
import io.odin.formatter.options.PositionFormat
import io.odin.formatter.options.ThrowableFormat

object OdinFormatters {}
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
