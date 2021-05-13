package wow.doge.http4sdemo

import cats.syntax.all._
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
import wow.doge.http4sdemo.routes.LibraryRoutes
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.services.NoopLibraryService

class BooksRoutesSpec extends UnitTestBase {

  val Root = Uri(path = "")

  test("get books api should succeed") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { logger =>
      val book = Book(1, "book1", "adsgq342dsdc", 1, date)
      val service = new NoopLibraryService {

        override def getBooks: Observable[Book] =
          Observable.fromIterable(book :: Nil)

      }
      for {
        _ <- IO.unit
        routes = new LibraryRoutes(service, logger).routes
        request = Request[Task](Method.GET, uri"/api/books")
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
    withReplayLogger { logger =>
      val service = new NoopLibraryService {
        override def updateBook(id: Int, updateData: BookUpdate) =
          IO.raiseError(
            LibraryService.EntityDoesNotExist(
              s"Book with id=$id does not exist"
            )
          )
      }

      for {
        _ <- IO.unit
        reqBody = BookUpdate(Some("blah"), None)
        routes = new LibraryRoutes(service, logger).routes
        request = Request[Task](Method.PATCH, Root / "api" / "books" / "1")
          .withEntity(reqBody)
        res <- routes.run(request).value
        body <- res.traverse(_.as[LibraryService.Error])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(assertEquals(res.map(_.status), Some(Status.NotFound)))
        _ <- IO(
          assertEquals(
            body,
            Some(
              LibraryService.EntityDoesNotExist("Book with id=1 does not exist")
            )
          )
        )

      } yield ()
    }
  }

  test("search books by author name api should succeed") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { logger =>
      val value = "blah"
      val books =
        List(Book(1, "book1", value, 1, date), Book(2, "book1", value, 1, date))
      val service = new NoopLibraryService {
        override def searchBook(mode: BookSearchMode, value: String) =
          mode match {
            case BookSearchMode.BookTitle =>
              Observable.raiseError(new NotImplementedError)
            case BookSearchMode.AuthorName =>
              Observable.fromIterable(books)
          }
      }
      for {
        _ <- IO.unit
        routes = new LibraryRoutes(service, logger).routes
        request = Request[Task](
          Method.GET,
          Root / "api" / "books"
            withQueryParams Map(
              "mode" -> BookSearchMode.AuthorName.entryName,
              "value" -> "blah"
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
    withReplayLogger { logger =>
      val value = "blah"
      val books =
        List(Book(1, "book1", value, 1, date), Book(2, "book1", value, 1, date))
      val service = new NoopLibraryService {
        override def searchBook(mode: BookSearchMode, value: String) =
          mode match {
            case BookSearchMode.BookTitle =>
              Observable.fromIterable(books)
            case BookSearchMode.AuthorName =>
              Observable.raiseError(new NotImplementedError)
          }
      }
      for {
        _ <- UIO.unit
        routes = new LibraryRoutes(service, logger).routes
        request = Request[Task](
          Method.GET,
          Root / "api" / "books"
            withQueryParams Map(
              "mode" -> BookSearchMode.BookTitle.entryName,
              "value" -> "blah"
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
    withReplayLogger { logger =>
      val service = new NoopLibraryService {
        override def getBookById(id: Int): UIO[Option[Book]] = UIO.none
      }
      for {
        _ <- UIO.unit
        routes = new LibraryRoutes(service, logger).routes
        request = Request[Task](
          Method.GET,
          Root / "api" / "books" / "12312"
        )
        res <- routes.run(request).value.hideErrors
        body <- res.traverse(_.as[LibraryService.Error])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(assertEquals(res.map(_.status), Some(Status.NotFound)))
        _ <- IO
          .pure(body)
          .assertEquals(
            Some(
              LibraryService
                .EntityDoesNotExist("Book with id 12312 does not exist")
            )
          )
      } yield ()
    }
  }
}
