package wow.doge.http4sdemo.routes

import cats.syntax.all._
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.endpoints.LibraryEndpoints2
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.utils.observableToJsonStreamA
import wow.doge.http4sdemo.services.LibraryService

// final class LibraryRoutes2(libraryService: LibraryService)(
//     logger: Logger[Task]
// ) extends server.Endpoints[Task]
//     with server.JsonEntitiesFromSchemas
//     // with server.ChunkedJsonEntities
//     with LibraryEndpoints {

//   override def textChunksRequest: http4s.Request[IO[Throwable, *]] => IO[
//     Throwable,
//     Either[http4s.Response[IO[Throwable, *]], Chunks[String]]
//   ] = ???

//   override def textChunksResponse: ResponseEntity[Chunks[String]] = ???

//   override def bytesChunksRequest: http4s.Request[IO[Throwable, *]] => IO[
//     Throwable,
//     Either[http4s.Response[IO[Throwable, *]], Chunks[Array[Byte]]]
//   ] = ???

//   override def bytesChunksResponse: ResponseEntity[Chunks[Array[Byte]]] = ???

//   override def jsonChunksRequest[A](implicit
//       codec: JsonCodec[A]
//   ): RequestEntity[Chunks[A]] = ???

//   override def jsonChunksResponse[A](implicit
//       codec: JsonCodec[A]
//   ): ResponseEntity[Chunks[A]] = ???
// }

final class LibraryRoutes2(libraryService: LibraryService)(
    logger: Logger[Task]
) {
  def getBookById(logger: Logger[Task], id: BookId): IO[AppError2, Book] = for {
    // _ <- clogger.infoU(s"Retrieving book")
    mbBook <- libraryService.getBookById(id)(logger)
    res <- mbBook match {
      case Some(value) => IO.pure(value)
      case None =>
        logger.warnU(s"Request for non-existent book") >>
          IO.raiseError(
            AppError2
              .EntityDoesNotExist(s"Book with id $id does not exist")
          )
    }
  } yield res

  val getBookByIdRoute: HttpRoutes[Task] =
    Http4sServerInterpreter.toRoutes(LibraryEndpoints2.getBookById) {
      case (ctx, id) =>
        logger.debugU(s"Header = $ctx") >>
          getBookById(logger, id).attempt
    }

  def getBooks(pagination: Pagination): UIO[Observable[Book]] = {

    // implicit val clogger =
    //   enrichLogger(logger, req, Map("name" -> "Get books"))
    // IO.deferAction(implicit s =>
    for {
      _ <- IO.unit
      // pagination = Pagination(page, limit)
      // _ <- clogger.infoU("Request for books")
      books = libraryService.getBooks(pagination)(logger)
      // res = observableArrayJsonEncoder[Task].toEntity(books.map(_.asJson))
    } yield books
    // )
  }

  val getBooksRoute: HttpRoutes[Task] =
    Http4sServerInterpreter.toRoutes(LibraryEndpoints2.getBooks) {
      case (ctx, pagination) =>
        getBooks(pagination)
          .flatMap(o => observableToJsonStreamA(o))
          .attempt
    }

  val routes: HttpRoutes[Task] = getBookByIdRoute <+> getBooksRoute

  // def toRoutes[I, E, O, F[_]](e: Endpoint[I, E, O, MonixStreams with WebSockets])(
  //     logic: I => F[Either[E, O]]
  // )(implicit serverOptions: Http4sServerOptions[F], fs: Concurrent[F], fcs: ContextShift[F], timer: Timer[F]): HttpRoutes[F] = {
  //   new EndpointToMonixHttp4sServer(serverOptions).toRoutes(e.serverLogic(logic))
  // }

  // def observableRoute[I, E, O, F[_]](
  //     e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets]
  // )(logic: I => F[Either[E, O]])(implicit
  //     serverOptions: Http4sServerOptions[F],
  //     fs: Concurrent[F],
  //     fcs: ContextShift[F],
  //     timer: Timer[F],
  //     S: O =:= Observable[Byte]
  // ): HttpRoutes[F] = {
  //   import wow.doge.http4sdemo.server.utils.observableArrayJsonEncoder
  //   Http4sServerInterpreter.toRoutes(e) { input =>
  //     EitherT(logic(input))
  //       .map(output => S.flip.apply(encoder.toEntity(output).body))
  //       .value
  //   }
  // }
}
