package wow.doge.http4sdemo.server.utils

import java.nio.charset.StandardCharsets

import cats.effect.Clock
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.syntax.all._
import io.odin.Level
import io.odin.Logger
import io.odin.LoggerMessage
import io.odin.formatter.Formatter
import io.odin.loggers.DefaultLogger
import monix.execution.Scheduler
import monix.nio.tcp.AsyncSocketChannelClient
import monix.reactive.Observable

final case class OdinMonixTcpLogger[F[_]: ContextShift: Clock](
    override val minLevel: Level,
    client: AsyncSocketChannelClient,
    formatter: Formatter
)(implicit F: ConcurrentEffect[F], s: Scheduler)
    extends DefaultLogger[F](minLevel) {

  private def writeln(
      msg: LoggerMessage,
      formatter: Formatter
  ): F[Unit] =
    for {
      cons <- client.tcpConsumer.to[F]
      written <- Observable
        .pure(
          (formatter.format(msg) + "\n")
            .getBytes(StandardCharsets.UTF_8)
        )
        .consumeWith(cons)
        .to[F]
      _ <- F.delay(println(s"Wrote $written bytes"))
    } yield ()

  override def withMinimalLevel(level: Level): Logger[F] =
    copy(minLevel = level)

  override def submit(msg: LoggerMessage): F[Unit] = writeln(msg, formatter)

}
