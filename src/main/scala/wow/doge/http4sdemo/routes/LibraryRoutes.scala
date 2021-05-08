package wow.doge.http4sdemo.routes

import fs2.interop.reactivestreams._
import io.circe.Codec
import io.circe.generic.semiauto._
import monix.bio.IO
import monix.bio.Task
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import wow.doge.http4sdemo.dto.Book
import wow.doge.http4sdemo.dto.BookSearchMode
import wow.doge.http4sdemo.dto.BookUpdate
import wow.doge.http4sdemo.dto.NewBook
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.utils.Logger

class LibraryRoutes(libraryService: LibraryService, logger: Logger) {

  val routes: HttpRoutes[Task] = {
    val dsl = Http4sDsl[Task]
    import dsl._
    object Value extends QueryParamDecoderMatcher[String]("value")
    HttpRoutes.of[Task] {

      case GET -> Root / "api" / "books" :?
          BookSearchMode.Matcher(mode) +& Value(value) =>
        import org.http4s.circe.streamJsonArrayEncoder
        import io.circe.syntax._
        IO.deferAction(implicit s =>
          for {
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

      case GET -> Root / "api" / "books" / IntVar(id) =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          bookJson <- libraryService.getBookById(id)
          res <- bookJson.fold(
            LibraryService
              .EntityDoesNotExist(s"Book with id $id does not exist")
              .toResponse
          )(b => Ok(b).hideErrors)
        } yield res

      case req @ PUT -> Root / "api" / "books" =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          newBook <- req.as[NewBook]
          res <- libraryService
            .insertBook(newBook)
            .tapError(err => logger.errorU(err.toString))
            .flatMap(book => Created(book).hideErrors)
            .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ PATCH -> Root / "api" / "books" / IntVar(id) =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          _ <- logger.infoU("wooterino")
          updateData <- req.as[BookUpdate]
          res <- libraryService
            .updateBook(id, updateData)
            .flatMap(_ => NoContent().hideErrors)
            .tapError(err => logger.errorU(err.toString))
            .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ DELETE -> Root / "api" / "books" / IntVar(id) =>
        for {
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
