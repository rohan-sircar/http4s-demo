package wow.doge.http4sdemo

import scala.util.Try

import io.odin.meta.Position
import io.odin.meta.Render
import monix.bio.IO
import monix.bio.Task
import monix.reactive.Observable
import slick.dbio.DBIO
import slick.dbio.DBIOAction
import slick.dbio.NoStream
import slick.dbio.Streaming
import slick.jdbc.JdbcBackend.DatabaseDef

package object implicits {
  implicit class DatabaseDefExt(private val db: DatabaseDef) extends AnyVal {
    def runL[R](a: DBIOAction[R, NoStream, Nothing]) =
      Task.deferFuture(db.run(a))

    def runTryL[R, A](a: DBIOAction[R, NoStream, Nothing])(implicit
        ev: R <:< Try[A]
    ) =
      Task.deferFuture(db.run(a)).flatMap(r => IO.fromTry(ev(r)))

    def streamO[T](a: DBIOAction[_, Streaming[T], Nothing]) =
      Observable.fromReactivePublisher(db.stream(a))
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

}
