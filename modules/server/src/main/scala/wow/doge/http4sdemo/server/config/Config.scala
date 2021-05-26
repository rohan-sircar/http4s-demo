package wow.doge.http4sdemo.server.utils.config
import scala.concurrent.duration.FiniteDuration

import enumeratum._
import eu.timepit.refined.pureconfig._
import eu.timepit.refined.types.numeric.PosInt
import pureconfig.generic.semiauto._
import pureconfig.module.enumeratum._

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

final case class AppConfig(
    loggerFormat: LoggerFormat,
    http: HttpConfig
)
object AppConfig {
  implicit val configReader = deriveConvert[AppConfig]
}
