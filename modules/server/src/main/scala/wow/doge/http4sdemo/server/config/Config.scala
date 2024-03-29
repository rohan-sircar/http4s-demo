package wow.doge.http4sdemo.server.config

import scala.concurrent.duration.FiniteDuration

import cats.data.NonEmptyList
import cats.syntax.all._
import emil.MailConfig
import emil.SSLType
import enumeratum._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.pureconfig._
import eu.timepit.refined.string.IPv4
import eu.timepit.refined.types.net
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string
import io.odin.{Level => OLevel}
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.error.FailureReason
import pureconfig.generic.semiauto._
import pureconfig.module.enumeratum._
import wow.doge.http4sdemo.refinements._

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

final case class RedisUrl private (inner: RedisUrl.Refinement)
object RedisUrl {
  type Refinement = string.NonEmptyFiniteString[100]
  object Refinement extends RefinedTypeOps[Refinement, String]

  def parseStr(s: String) = for {
    nes <- Refinement.from(s)
    res <- parseNes(nes)
  } yield res

  def parseNes(s: Refinement) = for {
    res <-
      if (s.value.startsWith("redis://")) Right(RedisUrl(s))
      else Left("Url should begin with `redis://`")
  } yield res

  implicit val configReader: ConfigReader[RedisUrl] =
    ConfigReader[Refinement].emap { s =>
      parseNes(s).leftMap(err =>
        CannotConvert(
          s.value,
          "RedisUrl",
          s"Failed to parse config value: Invalid format: $err"
        )
      )

    }
}

sealed trait AuthSessionConfig extends Product with Serializable
object AuthSessionConfig {
  case object RedisSession extends AuthSessionConfig {
    implicit val reader = deriveReader[RedisSession.type]
  }
  case object InMemory extends AuthSessionConfig {
    implicit val reader = deriveReader[InMemory.type]
  }

  implicit val configReader: ConfigReader[AuthSessionConfig] =
    deriveReader
}

final case class AuthConfig(
    secretKey: string.NonEmptyFiniteString[200],
    tokenTimeout: FiniteDuration,
    session: AuthSessionConfig
)
object AuthConfig {
  implicit val configReader = deriveReader[AuthConfig]
}

final case class RedisConfig(url: RedisUrl)
object RedisConfig {
  implicit val reader = deriveReader[RedisConfig]
}

final case class S3Config(
    endpoint: UrlRefinement,
    akid: string.NonEmptyFiniteString[100],
    sak: string.NonEmptyFiniteString[100],
    bucketName: string.NonEmptyFiniteString[100]
)
object S3Config {
  implicit val reader = deriveReader[S3Config]
}

final case class LogstashConfig(
    enabled: Boolean,
    host: String Refined IPv4,
    port: net.PortNumber
)

object LogstashConfig {
  implicit val reader = deriveReader[LogstashConfig]
}

final case class SmtpConfig(
    url: Option[String],
    username: Option[string.NonEmptyFiniteString[100]],
    password: Option[string.NonEmptyFiniteString[100]],
    ssl: Option[SSLType]
) { self =>

  def toMailConfig = (
    url.toRight(NonEmptyList.one("url is not set")),
    username.map(_.value).toRight(NonEmptyList.one("username is not set")),
    password.map(_.value).toRight(NonEmptyList.one("password is not set")),
    ssl.toRight(NonEmptyList.one("ssl is not set"))
  ).parMapN((_url, u, p, s) => MailConfig(_url, u, p, s))

  //   def toMailConfig = for {
  //   _url <- url
  //   u <- username.map(_.value)
  //   p <- password.map(_.value)
  //   s <- ssl
  // } yield MailConfig(_url, u, p, s)
}
object SmtpConfig {
  implicit val reader = deriveReader[SmtpConfig]
}
final case class AppConfig(
    http: HttpConfig,
    logger: LoggerConfig,
    auth: AuthConfig,
    redis: RedisConfig,
    s3: S3Config,
    logstash: LogstashConfig,
    smtp: SmtpConfig
)
object AppConfig {
  implicit val configReader = deriveReader[AppConfig]
}
