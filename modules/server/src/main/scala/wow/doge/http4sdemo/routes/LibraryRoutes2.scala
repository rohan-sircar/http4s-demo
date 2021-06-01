package wow.doge.http4sdemo.routes

import cats.syntax.all._
import io.odin.Logger
import io.odin.syntax._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.endpoints.LibraryEndpoints
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.utils.observableToJsonStreamA
import wow.doge.http4sdemo.services.LibraryService

final class LibraryRoutes2(libraryService: LibraryService)(
    logger: Logger[Task]
) {

  def getBookById(
      id: BookId
  )(implicit logger: Logger[Task]): IO[AppError2, Book] = for {
    _ <- logger.infoU(s"Retrieving book")
    mbBook <- libraryService.getBookById(id)
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
    Http4sServerInterpreter.toRoutes(LibraryEndpoints.getBookById) {
      case (ctx, id) =>
        getBookById(id)(
          logger.withConstContext(Map("reqCtx" -> ctx.toString))
        ).attempt
    }

  def getBooks(
      pagination: Pagination
  )(implicit logger: Logger[Task]): UIO[Observable[Book]] = {
    for {
      _ <- IO.unit
      _ <- logger.infoU(s"Retrieving books")
      books = libraryService.getBooks(pagination)
    } yield books
  }

  val getBooksRoute: HttpRoutes[Task] =
    Http4sServerInterpreter.toRoutes(LibraryEndpoints.getBooks) {
      case (ctx, pagination) =>
        getBooks(pagination)(
          logger.withConstContext(Map("reqCtx" -> ctx.toString))
        )
          .flatMap(o => observableToJsonStreamA(o))
          .attempt
    }

  val routes: HttpRoutes[Task] = getBookByIdRoute <+> getBooksRoute

}
