package wow.doge.http4sdemo.server
import cats.Show
import cats.effect.ConcurrentEffect
import cats.syntax.show._
import fs2.interop.reactivestreams._
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax._
import io.odin.Logger
import io.odin.syntax._
import monix.bio.Task
import monix.bio.UIO
import monix.execution.Scheduler
import monix.reactive.Observable
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.Request
import org.http4s.circe.streamJsonArrayDecoder
import org.http4s.circe.streamJsonArrayEncoder
import org.http4s.server.middleware.RequestId

package object utils {
  def extractReqId(req: Request[Task]) =
    req.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("null")

  def enrichLogger[S](
      L: Logger[Task],
      req: Request[Task],
      additionalContext: Map[String, S] = Map.empty[String, String]
  )(implicit S: Show[S]) = {
    L.withConstContext(
      Map(
        "request-id" -> extractReqId(req),
        "request-uri" -> req.uri.path
      ) ++ req.uri.query.multiParams.map { case key -> value =>
        key -> value.show
      }.toMap ++ additionalContext.map { case key -> value =>
        key -> value.show
      }.toMap
    )
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

  def observableToJsonStreamA[A: Encoder](obs: Observable[A]) =
    UIO.deferAction { implicit s =>
      UIO.pure(
        observableArrayJsonEncoder[Task].toEntity(obs.map(_.asJson)).body
      )
    }

}
