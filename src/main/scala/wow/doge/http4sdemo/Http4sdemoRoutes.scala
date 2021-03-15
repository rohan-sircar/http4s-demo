package wow.doge.http4sdemo

import cats.effect.Sync
import cats.implicits._
import fs2.interop.reactivestreams._
import io.circe.Codec
import io.circe.generic.semiauto._
import monix.bio.Task
import monix.reactive.Observable
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.dto.Book
import wow.doge.http4sdemo.dto.BookUpdate
import wow.doge.http4sdemo.dto.NewBook
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.slickcodegen.Tables._
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

  def userRoutes(userService: UserService): HttpRoutes[Task] = {
    val dsl = Http4sDsl[Task]
    import dsl._
    import org.http4s.circe.CirceEntityCodec._
    HttpRoutes.of[Task] { case GET -> Root / "users" =>
      Task.deferAction(implicit s =>
        for {
          _ <- Task.unit
          users = userService.users.toReactivePublisher.toStream[Task]
          res <- Ok(users)
        } yield res
      )
    }
  }

  def libraryRoutes(libraryService: LibraryService): HttpRoutes[Task] = {
    val dsl = Http4sDsl[Task]
    import dsl._
    HttpRoutes.of[Task] {
      case GET -> Root / "api" / "get" / "books" =>
        import org.http4s.circe.streamJsonArrayEncoder
        import io.circe.syntax._
        Task.deferAction(implicit s =>
          for {
            books <- Task.pure(
              libraryService.getBooks.toReactivePublisher
                .toStream[Task]
            )
            res <- Ok(books.map(_.asJson))
          } yield res
        )

      case GET -> Root / "api" / "get" / "book" / IntVar(id) =>
        // import org.http4s.circe.CirceEntityCodec._
        import org.http4s.circe.jsonEncoder
        import io.circe.syntax._
        for {
          bookJson <- libraryService.getBookById(id).map(_.asJson)
          res <- Ok(bookJson)
        } yield res

      case req @ POST -> Root / "api" / "post" / "book" =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          newBook <- req.as[NewBook]
          book <- libraryService.insertBook(newBook)
          res <- Created(book)
        } yield res

      case req @ PATCH -> Root / "api" / "update" / "book" / IntVar(id) =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          updateData <- req.as[BookUpdate]
          _ <- libraryService
            .updateBook(id, updateData)
            .void
            .onErrorHandleWith(ex =>
              Task(println(s"Handled -> ${ex.getMessage}"))
            )
          // .mapError(e => new Exception(e))
          res <- Ok()
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

case class User(id: String, email: String)
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

class UserService(profile: JdbcProfile, db: DatabaseDef) {
  import profile.api._
  def users: Observable[User] =
    Observable.fromReactivePublisher(
      db.stream(Users.map(u => (u.id, u.email).mapTo[User]).result)
    )

}
