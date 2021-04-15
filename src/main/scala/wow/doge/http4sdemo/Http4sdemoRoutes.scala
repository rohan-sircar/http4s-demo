package wow.doge.http4sdemo

import cats.effect.Sync
import cats.implicits._
import fs2.interop.reactivestreams._
import io.circe.Codec
import io.circe.generic.semiauto._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import wow.doge.http4sdemo.dto.Book
import wow.doge.http4sdemo.dto.BookSearchMode
import wow.doge.http4sdemo.dto.BookUpdate
import wow.doge.http4sdemo.dto.NewBook
import wow.doge.http4sdemo.services.LibraryService

object Http4sdemoRoutes {

  def jokeRoutes[F[_]: Sync](J: Jokes[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] { case GET -> Root / "joke" =>
      for {
        joke <- J.get
        resp <- Ok(joke)
      } yield resp
    }
  }

  def helloWorldRoutes[F[_]: Sync](H: HelloWorld[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] { case GET -> Root / "hello" / name =>
      for {
        greeting <- H.hello(HelloWorld.Name(name))
        resp <- Ok(greeting)
        r2 <- BadRequest("Bad request")
      } yield r2
    }
  }

  def libraryRoutes(libraryService: LibraryService): HttpRoutes[Task] = {
    val dsl = Http4sDsl[Task]
    import dsl._
    object Value extends QueryParamDecoderMatcher[String]("value")
    HttpRoutes.of[Task] {

      case GET -> Root / "api" / "get" / "book" :?
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

      case GET -> Root / "api" / "get" / "books" =>
        import org.http4s.circe.streamJsonArrayEncoder
        import io.circe.syntax._
        Task.deferAction(implicit s =>
          for {
            books <- IO.pure(
              libraryService.getBooks.toReactivePublisher
                .toStream[Task]
            )
            res <- Ok(books.map(_.asJson))
            // res <- Ok(streamJsonArrayEncoderOf[Task, Book].(books))
          } yield res
        )

      case GET -> Root / "blah" => Ok().hideErrors

      case GET -> Root / "api" / "get" / "book" / IntVar(id) =>
        import org.http4s.circe.CirceEntityCodec._
        // import org.http4s.circe.jsonEncoder
        // import io.circe.syntax._
        for {
          bookJson <- libraryService.getBookById(id)
          res <- Ok(bookJson)
        } yield res

      case req @ POST -> Root / "api" / "post" / "book" =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          newBook <- req.as[NewBook]
          // .onErrorHandleWith {
          //   case ParseF
          // }
          res <- libraryService
            .insertBook(newBook)
            .flatMap(book => Created(book).hideErrors)
            .mapErrorPartialWith {
              case LibraryService.EntityDoesNotExist(message) =>
                BadRequest(message).hideErrors
              case LibraryService.EntityAlreadyExists(message) =>
                BadRequest(message).hideErrors
              // case LibraryService.MyError2(_) => Ok().hideErrors
              // case C3                         => Ok().hideErrors
            }
        } yield res

      case req @ PATCH -> Root / "api" / "update" / "book" / IntVar(id) =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          updateData <- req.as[BookUpdate]
          res <- libraryService
            .updateBook(id, updateData)
            .flatMap(_ => Ok().hideErrors)
            .tapError(err => UIO(println(s"Handled -> ${err.toString}")))
            .mapErrorPartialWith {
              case e @ LibraryService.EntityDoesNotExist(message) =>
                BadRequest(e: LibraryService.Error).hideErrors
              // case LibraryService.MyError2(_) => Ok().hideErrors
              // case C3                         => Ok().hideErrors
            }
        } yield res

      case req @ DELETE -> Root / "api" / "delete" / "book" / IntVar(id) =>
        for {
          _ <- libraryService.deleteBook(id)
          res <- Ok()
        } yield res

      case req @ POST -> Root / "api" / "post" / "books" / "read" =>
        import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
        for {
          newBook <- req.as[List[Book]]
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
