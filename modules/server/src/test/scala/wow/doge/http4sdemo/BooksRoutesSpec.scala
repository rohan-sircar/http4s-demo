package wow.doge.http4sdemo

import cats.syntax.all._
import eu.timepit.refined.auto._
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.implicits._
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.pagination.Pagination
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.refinements._
import wow.doge.http4sdemo.routes.LibraryRoutes
import wow.doge.http4sdemo.services.NoopLibraryService

class BooksRoutesSpec extends UnitTestBase {

  val Root = Uri(path = "")

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
        ): Observable[Book] =
          Observable.fromIterable(book :: Nil)

      }
      for {
        _ <- IO.unit
        routes = new LibraryRoutes(service)(logger).routes
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
            AppError.EntityDoesNotExist(
              s"Book with id=$id does not exist"
            )
          )
      }

      for {
        _ <- IO.unit
        reqBody = BookUpdate(Some(BookTitle("blahblah")), None)
        routes = new LibraryRoutes(service)(logger).routes
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
              Observable.raiseError(new NotImplementedError)
            case BookSearchMode.AuthorName =>
              Observable.fromIterable(books)
          }
      }
      for {
        _ <- IO.unit
        routes = new LibraryRoutes(service)(logger).routes
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
              Observable.fromIterable(books)
            case BookSearchMode.AuthorName =>
              Observable.raiseError(new NotImplementedError)
          }
      }
      for {
        _ <- UIO.unit
        routes = new LibraryRoutes(service)(logger).routes
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
        routes = new LibraryRoutes(service)(logger).routes
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
}
