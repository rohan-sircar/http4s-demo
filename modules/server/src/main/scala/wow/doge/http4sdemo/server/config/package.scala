package wow.doge.http4sdemo.server

import cats.syntax.all._
import emil.SSLType
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

package object config {
  implicit val emilSslReader = ConfigReader[String].emap(s =>
    SSLType
      .fromString(s)
      .leftMap(err =>
        CannotConvert(
          s,
          "SSLType",
          s"Failed to parse config value: Invalid format: $err"
        )
      )
  )
}
