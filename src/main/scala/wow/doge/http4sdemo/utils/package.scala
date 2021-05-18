package wow.doge.http4sdemo

import scala.concurrent.duration.FiniteDuration

import cats.data.ValidatedNec
import cats.effect.ConcurrentEffect
import enumeratum._
import eu.timepit.refined.pureconfig._
import eu.timepit.refined.types.numeric.PosInt
import fs2.interop.reactivestreams._
import io.circe.Json
import io.scalaland.chimney.TransformerF
import monix.bio.IO
import monix.bio.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.http4s.EntityEncoder
import org.http4s.Request
import org.http4s.circe.streamJsonArrayEncoder
import org.http4s.server.middleware.RequestId
import pureconfig.generic.semiauto._
import pureconfig.module.enumeratum._

package object utils {
  type RefinementValidation[+A] = ValidatedNec[String, A]

  def extractReqId(req: Request[Task]) =
    req.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("null")

  def transformIntoL[A, B](src: A)(implicit
      T: TransformerF[RefinementValidation, A, B]
  ) = {
    IO.fromEither(T.transform(src).toEither)
      .mapError(errs => new Exception(s"Failed to convert: $errs"))
      .hideErrors
  }

  implicit def observableArrayJsonEncoder[F[_]: ConcurrentEffect](implicit
      S: Scheduler
  ): EntityEncoder[F, Observable[Json]] =
    EntityEncoder[F, fs2.Stream[F, Json]]
      .contramap[Observable[Json]](_.toReactivePublisher.toStream[F])
}
package utils {

  //not used currently
  final case class AppContext(reqId: String)

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

}
