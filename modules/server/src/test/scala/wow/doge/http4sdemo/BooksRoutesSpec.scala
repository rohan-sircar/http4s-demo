package wow.doge.http4sdemo.server

import cats.syntax.all._
import eu.timepit.refined.auto._
import io.odin.Logger
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.implicits._
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.pagination.Pagination
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.refinements._
import wow.doge.http4sdemo.server.AppError
import wow.doge.http4sdemo.server.routes.LibraryRoutes
import wow.doge.http4sdemo.server.routes.LibraryRoutes2
import wow.doge.http4sdemo.server.services.AuthServiceImpl
import wow.doge.http4sdemo.server.services.NoOpAuthService
import wow.doge.http4sdemo.server.services.NoopLibraryService

final class BooksRoutesSpec extends UnitTestBase {

  val fixture = ResourceFixture(
    AuthServiceImpl
      .inMemoryWithUser(suNewUser)(dummySigningKey, Logger.noop[Task])
  )

  test("get books api should succeed") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { implicit logger =>
      val book =
        Book(
          BookId(1),
          BookTitle("book1"),
          BookIsbn("adsgq342dsdc"),
          AuthorId(1),
          date
        )
      val service = new NoopLibraryService {

        override def getBooks(pagination: Pagination)(implicit
            L: Logger[Task]
        ): IO[AppError2, Observable[Book]] =
          IO.pure(Observable(book))

      }
      for {
        _ <- IO.unit
        authService = new NoOpAuthService
        routes = new LibraryRoutes2(service, authService)(
          logger
        ).routes
        request = Request[Task](
          Method.GET,
          uri"/api/books" withQueryParams Map(
            "page" -> 0,
            "limit" -> 5
          )
        )
        res <- routes.run(request).value
        body <- res.traverse(_.as[List[Book]])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Ok))
        _ <- IO(assertEquals(body, Some(List(book))))
      } yield ()
    }
  }

  test("update book api should fail gracefully when book does not exist") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { implicit logger =>
      val service = new NoopLibraryService {
        override def updateBook(id: BookId, updateData: BookUpdate)(implicit
            logger: Logger[Task]
        ) =
          IO.raiseError(
            AppError.EntityDoesNotExist(s"Book with id=$id does not exist")
          )
      }

      for {
        _ <- IO.unit
        authService = new NoOpAuthService
        reqBody = BookUpdate(Some(BookTitle("blahblah")), None)
        routes = new LibraryRoutes(service, authService)(logger).routes
        request = Request[Task](Method.PATCH, Root / "api" / "books" / "1")
          .withEntity(reqBody)
        res <- routes.run(request).value
        body <- res.traverse(_.as[AppError])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(assertEquals(res.map(_.status), Some(Status.NotFound)))
        _ <- IO(
          assertEquals(
            body,
            Some(
              AppError.EntityDoesNotExist("Book with id=1 does not exist")
            )
          )
        )

      } yield ()
    }
  }

  test("search books by author name api should succeed") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { implicit logger =>
      val value = BookIsbn("blahblah")
      val books =
        List(
          Book(BookId(1), BookTitle("book1"), value, AuthorId(1), date),
          Book(BookId(2), BookTitle("book1"), value, AuthorId(1), date)
        )
      val service = new NoopLibraryService {
        override def searchBooks(mode: BookSearchMode, value: SearchQuery)(
            implicit L: Logger[Task]
        ) =
          mode match {
            case BookSearchMode.BookTitle =>
              IO.terminate(new NotImplementedError)
            case BookSearchMode.AuthorName =>
              IO.pure(Observable.fromIterable(books))
          }
      }
      for {
        _ <- IO.unit
        authService = new NoOpAuthService
        routes = new LibraryRoutes(service, authService)(logger).routes
        request = Request[Task](
          Method.GET,
          Root / "api" / "books" / "search"
            withQueryParams Map(
              "mode" -> BookSearchMode.AuthorName.entryName,
              "q" -> "blahblah"
            )
        )
        res <- routes.run(request).value.hideErrors
        body <- res.traverse(_.as[List[Book]])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(assertEquals(res.map(_.status), Some(Status.Ok)))
        _ <- IO.pure(body).assertEquals(Some(books))
      } yield ()
    }
  }

  test("search books by book title api should succeed") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { implicit logger =>
      val value = BookIsbn("blahblah")
      val books =
        List(
          Book(BookId(1), BookTitle("book1"), value, AuthorId(1), date),
          Book(BookId(2), BookTitle("book1"), value, AuthorId(1), date)
        )
      val service = new NoopLibraryService {
        override def searchBooks(mode: BookSearchMode, value: SearchQuery)(
            implicit L: Logger[Task]
        ) =
          mode match {
            case BookSearchMode.BookTitle =>
              IO.pure(Observable.fromIterable(books))
            case BookSearchMode.AuthorName =>
              IO.terminate(new NotImplementedError)
          }
      }
      for {
        _ <- UIO.unit
        authService = new NoOpAuthService
        routes = new LibraryRoutes(service, authService)(logger).routes
        request = Request[Task](
          Method.GET,
          Root / "api" / "books" / "search"
            withQueryParams Map(
              "mode" -> BookSearchMode.BookTitle.entryName,
              "q" -> "blahblah"
            )
        )
        res <- routes.run(request).value.hideErrors
        body <- res.traverse(_.as[List[Book]])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(assertEquals(res.map(_.status), Some(Status.Ok)))
        _ <- IO.pure(body).assertEquals(Some(books))
      } yield ()
    }
  }

  test("get book by id should fail gracefully when book does not exist") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { implicit logger =>
      val service = new NoopLibraryService {
        override def getBookById(id: BookId)(implicit
            L: Logger[Task]
        ): UIO[Option[Book]] = UIO.none
      }
      for {
        _ <- UIO.unit
        authService = new NoOpAuthService
        routes = new LibraryRoutes2(service, authService)(
          logger
        ).routes
        request = Request[Task](
          Method.GET,
          Root / "api" / "books" / "12312"
        )
        res <- routes.run(request).value.hideErrors
        body <- res.traverse(_.as[AppError])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(assertEquals(res.map(_.status), Some(Status.NotFound)))
        _ <- IO
          .pure(body)
          .assertEquals(
            Some(
              AppError
                .EntityDoesNotExist("Book with id 12312 does not exist")
            )
          )
      } yield ()
    }
  }

  fixture.test("authed get book by id should succeed") {
    case (id, token, authService) =>
      import org.http4s.circe.CirceEntityCodec._
      withReplayLogger { implicit logger =>
        val book = Book(
          BookId(1),
          BookTitle("book1"),
          BookIsbn("blahblah"),
          AuthorId(1),
          date
        )
        val service = new NoopLibraryService {
          override def getBookById(id: BookId)(implicit
              L: Logger[Task]
          ): UIO[Option[Book]] = IO.some(book)
        }
        for {
          _ <- UIO.unit
          routes = new LibraryRoutes2(service, authService)(
            logger
          ).routes
          request = Request[Task](
            Method.GET,
            Root / "api" / "private" / "books" / "1"
          ).withHeaders(authHeader(token))
          res <- routes.run(request).value
          body <- res.traverse(_.as[Book])
          _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
          _ <- IO(assertEquals(res.map(_.status), Some(Status.Ok)))
          _ <- IO.pure(body).assertEquals(Some(book))
        } yield ()
      }
  }

  test("create book should succeed") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { implicit logger =>
      val nb = NewBook(BookTitle("somebook"), BookIsbn("adgqegqq"), AuthorId(1))
      val book = nb
        .into[Book]
        .withFieldConst(_.bookId, BookId(1))
        .withFieldConst(_.createdAt, date)
        .transform
      val service = new NoopLibraryService {
        override def createBook(nb: NewBook)(implicit
            L: Logger[Task]
        ): IO[AppError2, Book] = UIO(book)
      }
      for {
        _ <- UIO.unit
        authService = new NoOpAuthService
        routes = new LibraryRoutes2(service, authService)(
          logger
        ).routes
        request = Request[Task](
          Method.POST,
          Root / "api" / "books"
        ).withEntity(nb)
        res <- routes.run(request).value
        body <- res.traverse(_.as[Book])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(assertEquals(res.map(_.status), Some(Status.Created)))
        _ <- IO.pure(body).assertEquals(Some(book))
      } yield ()
    }
  }
}
