package wow.doge.http4sdemo

import enumeratum._
import monix.bio.IO
import monix.bio.Task
import org.http4s.Request
import org.http4s.server.middleware.RequestId
import pureconfig.generic.semiauto._
import pureconfig.module.enumeratum._

package object utils {
  def extractReqId(req: Request[Task]) = IO.pure(
    req.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("null")
  )
}
package utils {

  //not used currently
  final case class AppContext(reqId: String)

  sealed trait LoggerFormat extends EnumEntry with EnumEntry.Hyphencase
  object LoggerFormat extends Enum[LoggerFormat] {
    val values = findValues
    case object Json extends LoggerFormat
    case object Pretty extends LoggerFormat
    //TODO: Make PR to update the docs about this
    implicit val configReader = enumeratumConfigConvert[LoggerFormat]
  }

  final case class AppConfig(loggerFormat: LoggerFormat)
  object AppConfig {
    implicit val configReader = deriveConvert[AppConfig]
  }
}
