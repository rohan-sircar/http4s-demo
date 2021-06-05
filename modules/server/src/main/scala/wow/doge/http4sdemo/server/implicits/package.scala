package wow.doge.http4sdemo.server

import scala.util.Try

import cats.kernel.Eq
import cats.syntax.all._
import com.github.tminglei.slickpg.TsVector
import com.rms.miu.slickcats.DBIOInstances
import eu.timepit.refined.api._
import io.circe.generic.semiauto._
import monix.bio.IO
import monix.bio.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import slick.dbio
import slick.dbio.DBIO
import slick.dbio.DBIOAction
import slick.dbio.NoStream
import slick.dbio.Streaming
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.ResultSetConcurrency
import slick.jdbc.ResultSetType
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.tapir.client.sttp.WebSocketToPipe
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ServerEndpointInParts
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.server.utils.WebSocketToMonixPipe
import wow.doge.http4sdemo.server.{ExtendedPgProfile => JdbcProfile}
import wow.doge.http4sdemo.slickcodegen.Tables

package object implicits
    extends ColumnTypes
    with DBIOInstances
    with QueryParamDecoders {
  // import slickeffect.DBIOInstances

  implicit final class BookExt(private val B: Book.type) extends AnyVal {
    def fromBooksTableFn(implicit profile: JdbcProfile) = {
      import profile.api._
      (b: Tables.Books) =>
        (b.bookId, b.bookTitle, b.bookIsbn, b.authorId, b.createdAt).mapTo[Book]
    }
  }

  implicit final class NewBookExt(private val B: NewBook.type) extends AnyVal {
    def fromBooksTableFn(implicit profile: JdbcProfile) = {
      import profile.api._
      (b: Tables.Books) => (b.bookTitle, b.bookIsbn, b.authorId).mapTo[NewBook]
    }
  }

  implicit final class AuthorExt(private val B: Author.type) extends AnyVal {
    def fromAuthorsTableFn(implicit profile: JdbcProfile) = {
      import profile.api._
      (a: Tables.Authors) => (a.authorId, a.authorName).mapTo[Author]
    }
  }

  implicit final class ExtraExt(private val B: Extra.type) extends AnyVal {
    def fromExtrasTableFn(implicit profile: JdbcProfile) = {
      import profile.api._
      (e: Tables.Extras) =>
        (e.extrasId, e.color, e.metadataJson, e.content).mapTo[Extra]
    }
  }

  implicit final class NewExtraExt(private val B: NewExtra.type)
      extends AnyVal {

    def fromExtrasTableFn(implicit profile: JdbcProfile) = {
      import profile.api._
      (e: Tables.Extras) => (e.color, e.metadataJson, e.content).mapTo[NewExtra]
    }
  }

  implicit final class DatabaseDefExt(private val db: DatabaseDef)
      extends AnyVal {
    def runL[R](a: DBIOAction[R, NoStream, Nothing]) =
      Task.deferFuture(db.run(a))

    def runTryL[R, A](a: DBIOAction[R, NoStream, Nothing])(implicit
        ev: R <:< Try[A]
    ) =
      Task.deferFuture(db.run(a)).flatMap(r => IO.fromTry(ev(r)))

    //format: off
    def streamO[T](a: DBIOAction[_, Streaming[T], dbio.Effect.All with dbio.Effect.Transactional]
    )(implicit P: JdbcProfile) = {
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

  implicit final class DBIOExt(private val D: DBIO.type) extends AnyVal {
    def unit: DBIO[Unit] = D.successful(())
    def fromIO[T](io: IO[Throwable, T])(implicit s: monix.execution.Scheduler) =
      D.from(io.runToFuture)

    def traverse[R, B](in: Seq[R])(f: R => DBIO[B])(implicit
        s: monix.execution.Scheduler
    ) = in.traverse(f)
  }

  implicit val tsVectorDecoder = deriveDecoder[TsVector]
  implicit val tsVectorEncoder = deriveEncoder[TsVector]

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

  implicit def webSocketsSupportedForMonixStream(implicit
      s: Scheduler
  ): WebSocketToPipe[MonixStreams with WebSockets] =
    new WebSocketToMonixPipe[Task, MonixStreams with WebSockets]
  // implicit def webSocketsSupportedForMonixStreamAndEffect[F[_]: ConcurrentEffect]: WebSocketToPipe[Effect[F] with MonixStreams with WebSockets] =
  //   new WebSocketToMonixPipe[F, Effect[F] with MonixStreams with WebSockets]

  // implicit val qpdPaginationPage: QueryParamDecoder[PaginationPage] =
  //   QueryParamDecoder[PaginationRefinement]
  //     .coerce[QueryParamDecoder[PaginationPage]]
  // object matcher extends QueryParamDecoderMatcher[PaginationPage]("page")

  implicit val eqForJwtMac: Eq[JWTMac[HMACSHA256]] = Eq.instance {
    case (self, that) => self.toEncodedString === that.toEncodedString
  }

  implicit class ServerEndpointInPartsExt[U, IR, I, E, O, -R, F[_]](
      private val E: ServerEndpointInParts[U, IR, I, E, O, R, F]
  ) {
    def andThen2(
        remainingLogic: ((U, IR)) => F[Either[E, O]]
    ): ServerEndpoint[I, E, O, R, F] = E.andThen(remainingLogic)
  }
}
