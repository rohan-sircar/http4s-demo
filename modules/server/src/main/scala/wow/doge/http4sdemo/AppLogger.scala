package wow.doge.http4sdemo

import cats.Monad
import cats.effect.Clock
import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Timer
import io.odin.Logger
import io.odin.config.enclosureRouting
import io.odin.consoleLogger
import io.odin.json.{Formatter => JFormatter}
import io.odin.syntax._
import io.odin.{Level => OLevel}
import wow.doge.http4sdemo.server.config.Level
import wow.doge.http4sdemo.server.config.LoggerConfig
import wow.doge.http4sdemo.server.config.LoggerFormat
import wow.doge.http4sdemo.server.utils.PaddedFormatter

object AppLogger {
  def formatter(config: LoggerConfig) =
    config.format match {
      case LoggerFormat.Json   => JFormatter.json
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
