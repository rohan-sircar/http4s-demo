package wow.doge.http4sdemo.utils

import cats.effect.Sync
import cats.syntax.all._
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.odin.Level
import io.odin.Logger

final class StructuredOdinLogger[F[_]](val logger: Logger[F])(implicit
    F: Sync[F]
) extends SelfAwareStructuredLogger[F] {
  override def isTraceEnabled: F[Boolean] =
    F.delay(logger.minLevel <= Level.Trace)
  override def isDebugEnabled: F[Boolean] =
    F.delay(logger.minLevel <= Level.Debug)
  override def isInfoEnabled: F[Boolean] =
    F.delay(logger.minLevel <= Level.Info)
  override def isWarnEnabled: F[Boolean] =
    F.delay(logger.minLevel <= Level.Warn)
  override def isErrorEnabled: F[Boolean] =
    F.delay(logger.minLevel <= Level.Error)

  override def trace(t: Throwable)(msg: => String): F[Unit] =
    isTraceEnabled
      .ifM(F.delay(logger.trace(msg, t)), F.unit)
  override def trace(msg: => String): F[Unit] =
    isTraceEnabled
      .ifM(F.delay(logger.trace(msg)), F.unit)
  override def trace(ctx: Map[String, String])(msg: => String): F[Unit] =
    isTraceEnabled
      .ifM(F.delay(logger.trace(msg, ctx)), F.unit)
  override def debug(t: Throwable)(msg: => String): F[Unit] =
    isDebugEnabled
      .ifM(F.delay(logger.debug(msg, t)), F.unit)
  override def debug(msg: => String): F[Unit] =
    isDebugEnabled
      .ifM(F.delay(logger.debug(msg)), F.unit)
  override def debug(ctx: Map[String, String])(msg: => String): F[Unit] =
    isDebugEnabled
      .ifM(F.delay(logger.debug(msg, ctx)), F.unit)
  override def info(t: Throwable)(msg: => String): F[Unit] =
    isInfoEnabled
      .ifM(F.delay(logger.info(msg, t)), F.unit)
  override def info(msg: => String): F[Unit] =
    isInfoEnabled
      .ifM(F.delay(logger.info(msg)), F.unit)
  override def info(ctx: Map[String, String])(msg: => String): F[Unit] =
    isInfoEnabled
      .ifM(F.delay(logger.info(msg, ctx)), F.unit)
  override def warn(t: Throwable)(msg: => String): F[Unit] =
    isWarnEnabled
      .ifM(F.delay(logger.warn(msg, t)), F.unit)
  override def warn(msg: => String): F[Unit] =
    isWarnEnabled
      .ifM(F.delay(logger.warn(msg)), F.unit)
  override def warn(ctx: Map[String, String])(msg: => String): F[Unit] =
    isWarnEnabled
      .ifM(F.delay(logger.warn(msg, ctx)), F.unit)
  override def error(t: Throwable)(msg: => String): F[Unit] =
    isErrorEnabled
      .ifM(F.delay(logger.error(msg, t)), F.unit)
  override def error(msg: => String): F[Unit] =
    isErrorEnabled
      .ifM(F.delay(logger.error(msg)), F.unit)
  override def error(ctx: Map[String, String])(msg: => String): F[Unit] =
    isErrorEnabled
      .ifM(F.delay(logger.error(msg, ctx)), F.unit)
  override def trace(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isTraceEnabled
      .ifM(F.delay(logger.error(msg, t)), F.unit)
  override def debug(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isDebugEnabled
      .ifM(F.delay(logger.error(msg, t)), F.unit)
  override def info(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isInfoEnabled
      .ifM(F.delay(logger.error(msg, t)), F.unit)
  override def warn(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isWarnEnabled
      .ifM(F.delay(logger.error(msg, t)), F.unit)
  override def error(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isErrorEnabled
      .ifM(F.delay(logger.error(msg, t)), F.unit)
}
