package wow.doge.http4sdemo.server.utils

import java.nio.charset.StandardCharsets

import cats.effect.Clock
import cats.effect.Sync
import fs2.Chunk
import fs2.io.tcp.Socket
import io.odin.Level
import io.odin.Logger
import io.odin.LoggerMessage
import io.odin.formatter.Formatter
import io.odin.loggers.DefaultLogger

final case class OdinFs2TcpLogger[F[_]: Clock](
    override val minLevel: Level,
    socket: Socket[F],
    formatter: Formatter
)(implicit F: Sync[F])
    extends DefaultLogger[F](minLevel) {

  private def writeln(
      msg: LoggerMessage,
      formatter: Formatter
  ): F[Unit] = socket.write(
    Chunk.array(
      (formatter.format(msg) + "\n").getBytes(StandardCharsets.UTF_8)
    )
  )

  override def withMinimalLevel(level: Level): Logger[F] =
    copy(minLevel = level)

  override def submit(msg: LoggerMessage): F[Unit] = writeln(msg, formatter)

}
