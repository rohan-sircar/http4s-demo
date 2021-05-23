package wow.doge.http4sdemo

import cats.data.ValidatedNec
import cats.effect.ConcurrentEffect
import fs2.interop.reactivestreams._
import io.circe.Json
import io.scalaland.chimney.TransformerF
import monix.bio.IO
import monix.bio.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.Request
import org.http4s.circe.streamJsonArrayDecoder
import org.http4s.circe.streamJsonArrayEncoder
import org.http4s.server.middleware.RequestId

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
      .contramap(_.toReactivePublisher.toStream[F])

  implicit def observableArrayJsonDecoder[F[_]: ConcurrentEffect]
      : EntityDecoder[F, Observable[Json]] =
    EntityDecoder[F, fs2.Stream[F, Json]].map(stream =>
      Observable.fromReactivePublisher(stream.toUnicastPublisher)
    )

}
package utils {

  //not used currently
  final case class AppContext(reqId: String)
}
