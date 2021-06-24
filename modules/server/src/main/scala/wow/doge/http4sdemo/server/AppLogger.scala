package wow.doge.http4sdemo.server

import java.net.InetSocketAddress

import cats.Monad
import cats.effect.Blocker
import cats.effect.Clock
import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Timer
import fs2.io.tcp.SocketGroup
import io.odin.Logger
import io.odin.config.enclosureRouting
import io.odin.consoleLogger
import io.odin.syntax._
import io.odin.{Level => OLevel}
import wow.doge.http4sdemo.server.config.Level
import wow.doge.http4sdemo.server.config.LoggerConfig
import wow.doge.http4sdemo.server.config.LoggerFormat
import wow.doge.http4sdemo.server.config.LogstashConfig
import wow.doge.http4sdemo.server.utils.JsonFormatter
import wow.doge.http4sdemo.server.utils.OdinFs2TcpLogger
import wow.doge.http4sdemo.server.utils.PaddedFormatter

object AppLogger {
  def formatter(config: LoggerConfig) =
    config.format match {
      case LoggerFormat.Json   => JsonFormatter.formatter
      case LoggerFormat.Pretty => PaddedFormatter.default
      // Formatter.colorful
    }
  def apply[F[_]: ContextShift: Concurrent: Timer](
      config: LoggerConfig
  ): Resource[F, Logger[F]] = {
    consoleLogger[F](formatter(config), OLevel.Debug)
      .withSecretContext("password", "userPassword", "user_password")
      .withAsync(config.timeWindow, Some(config.bufferSize.value))
  }

  def routed[F[_]: ContextShift: Concurrent: Timer](
      config: LoggerConfig
  ): Resource[F, Logger[F]] =
    apply(config).map(logger => loggerRouter(logger, config.routes.value))

  def loggerRouter[F[_]: Clock: Monad](
      logger: Logger[F],
      routes: Map[String, Level]
  ): Logger[F] = {
    enclosureRouting[F](routes.map { case (packageName, level) =>
      packageName -> logger.withMinimalLevel(level.toOdin)
    }.toList: _*).withNoopFallback
  }

}

object LogstashLogger {
  def apply[F[_]: ContextShift: Timer](
      loggerConfig: LoggerConfig,
      logstashConfig: LogstashConfig,
      blocker: Blocker
  )(implicit F: Concurrent[F]): Resource[F, Logger[F]] =
    if (logstashConfig.enabled) for {
      socketGroup <- SocketGroup[F](blocker)
      addr <- Resource.eval(
        F.delay(
          new InetSocketAddress(
            logstashConfig.host.value,
            logstashConfig.port.value
          )
        )
      )
      socket <- socketGroup.client(addr)
      logger <- OdinFs2TcpLogger[F](
        OLevel.Debug,
        socket,
        JsonFormatter.formatter
      )
        .withSecretContext("password", "userPassword", "user_password")
        .withAsync(loggerConfig.timeWindow, Some(loggerConfig.bufferSize.value))
    } yield logger
    else Resource.pure(Logger.noop[F])

  def routed[F[_]: ContextShift: Concurrent: Timer](
      loggerConfig: LoggerConfig,
      logstashConfig: LogstashConfig,
      blocker: Blocker
  ): Resource[F, Logger[F]] =
    apply(loggerConfig, logstashConfig, blocker).map(logger =>
      AppLogger.loggerRouter(logger, loggerConfig.routes.value)
    )
}
