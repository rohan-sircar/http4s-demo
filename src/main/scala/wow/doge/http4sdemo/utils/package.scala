package wow.doge.http4sdemo

import cats.data.ValidatedNec
import enumeratum._
import io.scalaland.chimney.TransformerF
import monix.bio.IO
import monix.bio.Task
import org.http4s.Request
import org.http4s.server.middleware.RequestId
import pureconfig.generic.semiauto._
import pureconfig.module.enumeratum._

package object utils {
  type RefinementValidation[+A] = ValidatedNec[String, A]

  def extractReqId(req: Request[Task]) = IO.pure(
    req.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("null")
  )

  def transformIntoL[A, B](src: A)(implicit
      T: TransformerF[RefinementValidation, A, B]
  ) = {
    IO.fromEither(T.transform(src).toEither)
      .mapError(errs => new Exception(s"Failed to convert: $errs"))
      .hideErrors
  }
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
