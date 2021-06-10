package wow.doge.http4sdemo

import cats.data.ValidatedNec
import cats.effect.ConcurrentEffect
import cats.syntax.either._
import eu.timepit.refined.api.RefType
import eu.timepit.refined.api.Validate
import fs2.interop.reactivestreams._
import io.odin.meta.Position
import io.odin.meta.Render
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.TransformerF
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.execution.Scheduler
import monix.reactive.Observable
import shapeless.ops.product
import shapeless.syntax.std.product._

package object implicits extends MyCirceSupportParser {
  // with slickeffect.DBIOInstances

  implicit final class MonixEvalTaskExt[T](private val task: monix.eval.Task[T])
      extends AnyVal {
    def toIO = IO.deferAction(implicit s => IO.from(task))
  }

  implicit final class MonixBioTaskExt[T](private val task: monix.bio.Task[T])
      extends AnyVal {
    def toTask =
      monix.eval.Task.deferAction(implicit s => monix.eval.Task.from(task))
  }

  implicit final class OdinLoggerExt(private val logger: io.odin.Logger[Task])
      extends AnyVal {
    def debugU[M](msg: => M)(implicit render: Render[M], position: Position) =
      logger.debug(msg).hideErrors
    def infoU[M](msg: => M)(implicit render: Render[M], position: Position) =
      logger.info(msg).hideErrors
    def traceU[M](msg: => M)(implicit render: Render[M], position: Position) =
      logger.trace(msg).hideErrors
    def warnU[M](msg: => M)(implicit render: Render[M], position: Position) =
      logger.warn(msg).hideErrors
    def errorU[M](msg: => M)(implicit render: Render[M], position: Position) =
      logger.error(msg).hideErrors
  }

  implicit final def vnecRefinedTransformerFrom[T, P, F[_, _]](implicit
      V: Validate[T, P],
      R: RefType[F]
  ): TransformerF[ValidatedNec[String, +*], T, F[T, P]] = (src: T) =>
    R.refine(src).toValidatedNec

  implicit final def eitherRefinedTransformerFrom[T, P, F[_, _]](implicit
      V: Validate[T, P],
      R: RefType[F]
  ): TransformerF[Either[String, +*], T, F[T, P]] = (src: T) => R.refine(src)

  implicit final def optionRefinedTransformerFrom[T, P, F[_, _]](implicit
      V: Validate[T, P],
      R: RefType[F]
  ): TransformerF[Option, T, F[T, P]] = (src: T) => R.refine(src).toOption

  implicit final def refinedTransformerTo[P, T, F[_, _]](implicit
      R: RefType[F]
  ): Transformer[F[P, T], P] = src => R.unwrap(src)

  implicit final class TransformerExt[A](private val src: A) extends AnyVal {
    def transformL[B](implicit
        T: TransformerF[ValidatedNec[String, +*], A, B]
    ) = {
      IO.fromEither(T.transform(src).toEither)
        .mapError(errs => new Exception(s"Failed to convert: $errs"))
        .hideErrors
    }

  }

  implicit final class StreamToObs[T](private val S: fs2.Stream[Task, T]) {
    def toObs = {
      UIO.deferAction { implicit s =>
        implicit val C = ConcurrentEffect[Task]
        UIO(Observable.fromReactivePublisher(S.toUnicastPublisher))
      }
    }
  }
  implicit final class ObsToStream[T](private val O: Observable[T])
      extends AnyVal {
    def toStream[F[_]](implicit F: ConcurrentEffect[F], s: Scheduler) =
      O.toReactivePublisher.toStream[F]

    def toStreamIO =
      IO.deferAction(implicit s => IO(O.toReactivePublisher.toStream[Task]))
  }

  implicit final class ProductExt[T <: Product](private val P: T)
      extends AnyVal {
    def toStringMap(implicit
        toMap: product.ToMap.Aux[T, Symbol, Any]
    ): Map[String, String] = P.toMap[Symbol, Any].map { case k -> v =>
      k.name -> v.toString
    }
  }

}
