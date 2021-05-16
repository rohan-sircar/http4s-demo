package wow.doge.http4sdemo

import scala.util.Try

import cats.data.ValidatedNec
import cats.syntax.either._
import eu.timepit.refined.api._
import io.odin.meta.Position
import io.odin.meta.Render
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.TransformerF
import monix.bio.IO
import monix.bio.Task
import monix.reactive.Observable
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import slick.dbio.DBIO
import slick.dbio.DBIOAction
import slick.dbio.NoStream
import slick.dbio.Streaming
import slick.jdbc.JdbcBackend.DatabaseDef
import wow.doge.http4sdemo.utils.RefinementValidation
import wow.doge.http4sdemo.utils.transformIntoL
import slick.dbio
import slick.jdbc.ResultSetType
import slick.jdbc.ResultSetConcurrency
import slick.jdbc.JdbcProfile

package object implicits {
  implicit final class DatabaseDefExt(private val db: DatabaseDef)
      extends AnyVal {
    def runL[R](a: DBIOAction[R, NoStream, Nothing]) =
      Task.deferFuture(db.run(a))

    def runTryL[R, A](a: DBIOAction[R, NoStream, Nothing])(implicit
        ev: R <:< Try[A]
    ) =
      Task.deferFuture(db.run(a)).flatMap(r => IO.fromTry(ev(r)))

    //format: off
    def streamO[T](a: DBIOAction[_, Streaming[T], dbio.Effect.All with dbio.Effect.Transactional])(implicit P: JdbcProfile) = {
    //format: on
      import P.api._
      Observable.fromReactivePublisher(
        db.stream(
          a.withStatementParameters(
            rsType = ResultSetType.ForwardOnly,
            rsConcurrency = ResultSetConcurrency.ReadOnly,
            fetchSize = 10000
          ).transactionally
        )
      )
    }
  }

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

  implicit final class DBIOExt(private val D: DBIO.type) extends AnyVal {
    def unit = D.successful(())
    def fromIO[T](io: IO[Throwable, T])(implicit s: monix.execution.Scheduler) =
      D.from(io.runToFuture)
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

  implicit final def refinedQueryParamDecoder[T, P, F[_, _]](implicit
      Q: QueryParamDecoder[T],
      V: Validate[T, P],
      R: RefType[F]
  ): QueryParamDecoder[F[T, P]] =
    Q.emap { v =>
      R
        .refine(v)
        .leftMap(err =>
          ParseFailure(
            s"Error parsing query param ${v}",
            s"""
            | Error parsing query param ${v}
            | Details: $err
          """.stripMargin
          )
        )
    }

  implicit final class TransformerExt[A](private val src: A) extends AnyVal {
    def transformL[B](implicit T: TransformerF[RefinementValidation, A, B]) =
      transformIntoL(src)(T)

  }

}
