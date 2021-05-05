package wow.doge.http4sdemo

import cats.syntax.all._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.implicits._
import wow.doge.http4sdemo.MonixBioSuite
import wow.doge.http4sdemo.dto.Book
import wow.doge.http4sdemo.dto.BookSearchMode
import wow.doge.http4sdemo.dto.BookUpdate
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.services.NoopLibraryService

class LibraryControllerSpec extends MonixBioSuite {

  val Root = Uri(path = "")

  test("get books success") {
    import org.http4s.circe.CirceEntityCodec._
    val book = Book(1, "book1", "adsgq342dsdc", 1, date)
    val service = new NoopLibraryService {

      override def getBooks: Observable[Book] =
        Observable.fromIterable(book :: Nil)

      override def getBookById(id: Int): Task[Option[Book]] =
        Task.some(book)

    }
    for {
      _ <- UIO.unit
      routes = Http4sdemoRoutes.libraryRoutes(service)
      res <- routes
        .run(Request[Task](Method.GET, uri"/api/get/books"))
        .value
        .hideErrors
      body <- res.map(_.as[List[Book]]).sequence
      _ <- UIO(assertEquals(body, Some(List(book))))
      // _ <- logger2.debug(body.toString).hideErrors
    } yield ()
  }

  test("update book error") {
    import org.http4s.circe.CirceEntityCodec._
    val service = new NoopLibraryService {
      override def updateBook(id: Int, updateData: BookUpdate) =
        IO.raiseError(
          LibraryService.EntityDoesNotExist(s"Book with id=$id does not exist")
        )
    }

    for {
      _ <- UIO.unit
      reqBody = BookUpdate(Some("blah"), None)
      routes = Http4sdemoRoutes.libraryRoutes(service)
      res <- routes
        .run(
          Request[Task](Method.PATCH, Root / "api" / "update" / "book" / "1")
            .withEntity(reqBody)
        )
        .value
        .hideErrors
      body <- res.map(_.as[LibraryService.Error]).sequence
      _ <- UIO(
        assertEquals(
          body,
          Some(
            LibraryService.EntityDoesNotExist("Book with id=1 does not exist")
          )
        )
      )
      // _ <- logger.debug(res.toString).hideErrors
      // _ <- logger.debug(body.toString).hideErrors
    } yield ()
  }

  test("get books by author name") {
    import org.http4s.circe.CirceEntityCodec._
    val value = "blah"
    val books =
      List(Book(1, "book1", value, 1, date), Book(2, "book1", value, 1, date))
    val service = new NoopLibraryService {
      override def searchBook(mode: BookSearchMode, value: String) =
        mode match {
          case BookSearchMode.BookTitle =>
            Observable.fromIterable(books)
          case BookSearchMode.AuthorName =>
            Observable.fromIterable(books)
        }
    }
    for {
      _ <- UIO.unit
      // logger2 = logger.withConstContext(
      //   Map("Test" -> "get books by author name")
      // )
      routes = Http4sdemoRoutes.libraryRoutes(service)
      request = Request[Task](
        Method.GET,
        Root / "api" / "get" / "book"
          withQueryParams Map(
            "mode" -> BookSearchMode.AuthorName.entryName,
            "value" -> "blah"
          )
      )
      // _ <- logger2.info(s"Request -> $request")
      res <- routes.run(request).value.hideErrors
      body <- res.map(_.as[List[Book]]).sequence
      _ <- UIO.pure(body).assertEquals(Some(books))
      // _ <- logger2.debug(s"Response body -> $body").hideErrors
    } yield ()
  }

  test("get books by book title") {
    import org.http4s.circe.CirceEntityCodec._
    val value = "blah"
    val books =
      List(Book(1, "book1", value, 1, date), Book(2, "book1", value, 1, date))
    val service = new NoopLibraryService {
      override def searchBook(mode: BookSearchMode, value: String) =
        mode match {
          case BookSearchMode.BookTitle =>
            Observable.fromIterable(books)
          case BookSearchMode.AuthorName =>
            Observable.fromIterable(books)
        }
    }
    for {
      _ <- UIO.unit
      // logger2 = logger.withConstContext(
      //   Map("Test" -> "get books by book title")
      // )
      routes = Http4sdemoRoutes.libraryRoutes(service)
      request = Request[Task](
        Method.GET,
        Root / "api" / "get" / "book"
          withQueryParams Map(
            "mode" -> BookSearchMode.BookTitle.entryName,
            "value" -> "blah"
          )
      )
      // _ <- logger2.info(s"Request -> $request")
      res <- routes.run(request).value.hideErrors
      body <- res.map(_.as[List[Book]]).sequence
      _ <- UIO.pure(body).assertEquals(Some(books))
      // _ <- logger2.debug(s"Response body -> $body").hideErrors
    } yield ()
  }
}
