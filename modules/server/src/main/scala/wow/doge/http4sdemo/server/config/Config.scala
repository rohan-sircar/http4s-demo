package wow.doge.http4sdemo.server.config

import cats.syntax.all._
import enumeratum._
import eu.timepit.refined.pureconfig._
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string
import io.odin.{Level => OLevel}
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.error.FailureReason
import pureconfig.generic.semiauto._
import pureconfig.generic.auto._
import pureconfig.module.enumeratum._

import scala.concurrent.duration.FiniteDuration
// import pureconfig.generic.CoproductHint
// import pureconfig.generic.CoproductReaderOptions._

private[config] final case class ListWrapper(list: List[String])
private[config] object ListWrapper {
  implicit val reader = deriveReader[ListWrapper]
}

sealed trait Level extends EnumEntry with EnumEntry.Lowercase { self =>
  def toOdin: OLevel = self match {
    case Level.Trace => OLevel.Trace
    case Level.Debug => OLevel.Debug
    case Level.Info  => OLevel.Info
    case Level.Warn  => OLevel.Warn
    case Level.Error => OLevel.Error
  }
}

object Level extends Enum[Level] {
  val values = findValues
  case object Trace extends Level
  case object Debug extends Level
  case object Info extends Level
  case object Warn extends Level
  case object Error extends Level
}

final case class LoggerRoutes private (value: Map[String, Level])
object LoggerRoutes {
  implicit val configReader: ConfigReader[LoggerRoutes] =
    ConfigReader[String]
      .emap { s =>
        ConfigSource
          .string(s)
          .load[ListWrapper]
          .leftMap(e =>
            CannotConvert(
              s,
              "List",
              s"Failed to parse config value into list. Nested error was: [$e]"
            )
          )
          .flatMap(
            _.list.foldLeftM[Either[FailureReason, *], Map[String, Level]](
              Map.empty
            ) { case (acc, s) =>
              s.split(">>") match {
                case Array(key, value) =>
                  Level
                    .withNameOption(value) match {
                    case Some(v) => Right(acc + (key -> v))
                    case None =>
                      Left(
                        CannotConvert(
                          value,
                          "Level",
                          s"Failed to parse config value: not a valid log level"
                        )
                      )
                  }
                case _ =>
                  Left(
                    CannotConvert(
                      s,
                      "LoggerRoutes",
                      s"Failed to parse config value: Invalid format"
                    )
                  )
              }
            }
          )
      }
      .map(LoggerRoutes.apply)
}

final case class ThrottleConfig(amount: PosInt, per: FiniteDuration)
object ThrottleConfig {
  implicit val configReader = deriveConvert[ThrottleConfig]
}

final case class HttpConfig(throttle: ThrottleConfig, timeout: FiniteDuration)
object HttpConfig {
  implicit val configReader = deriveConvert[HttpConfig]
}

sealed trait LoggerFormat extends EnumEntry with EnumEntry.Hyphencase
object LoggerFormat extends Enum[LoggerFormat] {
  val values = findValues
  case object Json extends LoggerFormat
  case object Pretty extends LoggerFormat
  //TODO: Make PR to update the docs about this
  implicit val configReader = enumeratumConfigConvert[LoggerFormat]
}

final case class LoggerConfig(
    format: LoggerFormat,
    routes: LoggerRoutes,
    timeWindow: FiniteDuration,
    bufferSize: PosInt
)
object LoggerConfig {
  implicit val configReader = deriveReader[LoggerConfig]
}

final case class RedisUrl private (inner: string.NonEmptyFiniteString[100])
object RedisUrl {
  implicit val configReader: ConfigReader[RedisUrl] =
    ConfigReader[string.NonEmptyFiniteString[100]].emap { s =>
      if (s.value.startsWith("redis://")) Right(RedisUrl(s))
      else
        Left(
          CannotConvert(
            s.value,
            "RedisUrl",
            s"Failed to parse config value: Invalid format"
          )
        )
    }
}

sealed trait AuthSessionConfig extends Product with Serializable
object AuthSessionConfig {
  final case class RedisSession(url: RedisUrl) extends AuthSessionConfig
  object RedisSession {
    implicit val reader = deriveReader[RedisSession]
  }
  case object InMemory extends AuthSessionConfig {
    implicit val reader = deriveReader[InMemory.type]
  }
  implicit val configReader = deriveReader[AuthSessionConfig]
}

final case class AuthConfig(
    secretKey: string.NonEmptyFiniteString[200],
    tokenTimeout: FiniteDuration,
    session: AuthSessionConfig
)
object AuthConfig {
  implicit val configReader = deriveReader[AuthConfig]
}

final case class AppConfig(
    http: HttpConfig,
    logger: LoggerConfig,
    auth: AuthConfig
)
object AppConfig {
  implicit val configReader = deriveReader[AppConfig]
}
