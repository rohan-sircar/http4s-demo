package wow.doge.http4sdemo.routes

import io.circe.Json
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.reactive.Consumer
import monix.reactive.Observable
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.NewExtra
import wow.doge.http4sdemo.models.Refinements._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.server.utils.enrichLogger
import wow.doge.http4sdemo.services.LibraryService

final class LibraryRoutes(libraryService: LibraryService)(
    logger: Logger[Task]
) {

  val routes: HttpRoutes[Task] = {
    val dsl = Http4sDsl[Task]
    import dsl._
    object BookSearchQuery extends QueryParamDecoderMatcher[SearchQuery]("q")
    object StringSearchQuery extends QueryParamDecoderMatcher[String]("q")
    HttpRoutes.of[Task] {

      case req @ GET -> Root / "api" / "books" / "search" :?
          BookSearchMode.Matcher(mode) +& BookSearchQuery(query) =>
        import wow.doge.http4sdemo.utils.observableArrayJsonEncoder
        import io.circe.syntax._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Search book"))
        IO.deferAction(implicit s =>
          for {
            _ <- clogger.debugU("Request to search book")
            books = libraryService.searchBooks(mode, query)
            res <- Ok(books.map(_.asJson))
          } yield res
        )

      case req @ GET -> Root / "api" / "books" :?
          PaginationLimit.matcher(limit) +& PaginationPage.matcher(page) =>
        import wow.doge.http4sdemo.utils.observableArrayJsonEncoder
        import io.circe.syntax._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Get books"))
        IO.deferAction(implicit s =>
          for {
            _ <- IO.unit
            pagination = Pagination(page, limit)
            _ <- clogger.infoU("Request for books")
            books = libraryService.getBooks(pagination)
            res <- Ok(books.map(_.asJson))
          } yield res
        )

      case req @ GET -> Root / "api" / "books" / BookId(id) =>
        import org.http4s.circe.CirceEntityCodec._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Get book by id"))
        for {
          _ <- clogger.infoU(s"Retrieving book")
          bookJson <- libraryService.getBookById(id)
          res <- bookJson.fold(
            clogger.warnU(s"Request for non-existent book") >>
              AppError
                .EntityDoesNotExist(s"Book with id $id does not exist")
                .toResponse
          )(b => Ok(b).hideErrors)
        } yield res

      case req @ PUT -> Root / "api" / "books" =>
        import org.http4s.circe.CirceEntityCodec._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Create book"))
        for {
          newBook <- req.as[NewBook]
          res <- libraryService
            .createBook(newBook)
            .tapError(err => clogger.errorU(err.toString))
            .flatMap(book => Created(book).hideErrors)
            .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ PATCH -> Root / "api" / "books" / BookId(id) =>
        import org.http4s.circe.CirceEntityCodec._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Update book"))
        for {
          updateData <- req.as[BookUpdate]
          res <- libraryService
            .updateBook(id, updateData)
            .flatMap(_ => NoContent().hideErrors)
            .tapError(err => clogger.errorU(err.toString))
            .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ DELETE -> Root / "api" / "books" / BookId(id) =>
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Delete book"))
        for {
          _ <- clogger.debug("Request to delete book")
          _ <- libraryService.deleteBook(id)
          res <- Ok()
        } yield res

      case req @ POST -> Root / "api" / "books" =>
        import org.http4s.circe.CirceEntityCodec._
        import wow.doge.http4sdemo.utils.observableArrayJsonDecoder
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Search book"))
        IO.deferAction(implicit s =>
          for {
            // newBooks <- req.as[List[Book]]
            newBooks <- req.as[Observable[Json]]
            numRows <- newBooks
              .mapEvalF(_.as[NewBook])
              .bufferTumbling(50)
              .scanEvalF(Task.pure(NumRows(0))) { case (numRows, books) =>
                libraryService
                  .createBooks(books.toList)
                  .map(o => numRows :+ o.getOrElse(NumRows(0)))
              }
              .consumeWith(Consumer.foldLeft(NumRows(0))(_ :+ _))
              .toIO
            res <- Ok(numRows)
          } yield res
        )

      case req @ GET -> Root / "api" / "extras" / "search" :?
          StringSearchQuery(q) =>
        import wow.doge.http4sdemo.utils.observableArrayJsonEncoder
        import io.circe.syntax._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Search book"))
        IO.deferAction(implicit s =>
          for {
            _ <- clogger.infoU("Request for searching extras")
            extras = libraryService.searchExtras(q)
            res <- Ok(extras.map(_.asJson))
          } yield res
        )

      case req @ PUT -> Root / "api" / "extras" =>
        import org.http4s.circe.CirceEntityCodec._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Search book"))
        for {
          newExtra <- req.as[NewExtra]
          _ <- clogger.infoU("Request for creating extras")
          res <- libraryService
            .createExtra(newExtra)
            .flatMap(id => Created(id).hideErrors)
          // .onErrorHandleWith(_.toResponse)
        } yield res
    }
  }

}
