package wow.doge.http4sdemo.routes

import fs2.interop.reactivestreams._
import io.circe.Codec
import io.circe.generic.semiauto._
import io.odin.Logger
import io.odin.syntax._
import monix.bio.IO
import monix.bio.Task
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.Refinements._
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.utils.extractReqId

class LibraryRoutes(libraryService: LibraryService, logger: Logger[Task]) {

  val routes: HttpRoutes[Task] = {
    val dsl = Http4sDsl[Task]
    import dsl._
    object BookSearchValue
        extends QueryParamDecoderMatcher[StringRefinement]("value")
    HttpRoutes.of[Task] {

      case req @ GET -> Root / "api" / "books" / "search" :?
          BookSearchMode.Matcher(mode) +& BookSearchValue(value) =>
        import org.http4s.circe.streamJsonArrayEncoder
        import io.circe.syntax._
        IO.deferAction(implicit s =>
          for {
            reqId <- extractReqId(req)
            clogger = logger.withConstContext(
              Map(
                "name" -> "Search book",
                "request-id" -> reqId,
                "request-uri" -> req.uri.toString,
                "mode" -> mode.entryName,
                "value" -> value.value
              )
            )
            _ <- clogger.debugU("Request to search book")
            books <- IO.pure(
              libraryService
                .searchBook(mode, value)
                .toReactivePublisher
                .toStream[Task]
            )
            res <- Ok(books.map(_.asJson))
          } yield res
        )

      case GET -> Root / "api" / "books" =>
        import org.http4s.circe.streamJsonArrayEncoder
        import io.circe.syntax._
        Task.deferAction(implicit s =>
          for {
            _ <- logger.infoU("Got request for books")
            books <- IO.pure(
              libraryService.getBooks.toReactivePublisher
                .toStream[Task]
            )
            res <- Ok(books.map(_.asJson))
          } yield res
        )

      case req @ GET -> Root / "api" / "books" / BookId(id) =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          reqId <- extractReqId(req)
          clogger = logger.withConstContext(
            Map(
              "name" -> "Get book",
              "request-id" -> reqId,
              "request-uri" -> req.uri.toString,
              "book-id" -> id.toString
            )
          )
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
        for {
          reqId <- extractReqId(req)
          newBook <- req.as[NewBook]
          clogger = logger.withConstContext(
            Map(
              "name" -> "Create book",
              "request-id" -> reqId,
              "new-book-data" -> newBook.toString
            )
          )
          res <- libraryService
            .insertBook(newBook)
            .tapError(err => clogger.errorU(err.toString))
            .flatMap(book => Created(book).hideErrors)
            .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ PATCH -> Root / "api" / "books" / BookId(id) =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          reqId <- extractReqId(req)
          updateData <- req.as[BookUpdate]
          clogger = logger.withConstContext(
            Map(
              "name" -> "Update book",
              "request-id" -> reqId,
              "book-id" -> id.toString,
              "update-data" -> updateData.toString
            )
          )
          res <- libraryService
            .updateBook(id, updateData)
            .flatMap(_ => NoContent().hideErrors)
            .tapError(err => clogger.errorU(err.toString))
            .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ DELETE -> Root / "api" / "books" / BookId(id) =>
        for {
          reqId <- extractReqId(req)
          clogger = logger.withConstContext(
            Map(
              "name" -> "Delete book",
              "request-id" -> reqId,
              "book-id" -> id.toString
            )
          )
          _ <- clogger.debug("Request to delete book")
          _ <- libraryService.deleteBook(id)
          res <- Ok()
        } yield res

      //TODO: use convenience method for decoding json stream
      case req @ POST -> Root / "api" / "books" =>
        import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
        for {
          newBooks <- req.as[List[Book]]
          // obs = Observable.fromIterable(newBooks)
          // book <- libraryService.insertBook(newBook)
          res <- Ok("blah")
        } yield res
    }
  }

}

final case class User(id: String, email: String)
object User {
  val tupled = (this.apply _).tupled
  // implicit val decoder: Decoder[User] = deriveDecoder
  // implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, User] =
  //   jsonOf
  // implicit val encoder: Encoder[User] = deriveEncoder
  // implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, User] =
  //   jsonEncoderOf
  implicit val codec: Codec[User] = deriveCodec
}
