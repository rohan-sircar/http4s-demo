package wow.doge.http4sdemo

import cats.syntax.all._
import com.dimafeng.testcontainers.PostgreSQLContainer
import monix.bio.UIO
import wow.doge.http4sdemo.dto.BookSearchMode
import wow.doge.http4sdemo.dto.NewAuthor
import wow.doge.http4sdemo.dto.NewBook
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.services.LibraryServiceImpl

class LibraryServiceSpec extends DatabaseIntegrationTestBase {

  override def afterContainersStart(containers: Containers): Unit = {
    super.afterContainersStart(containers)
    createSchema(containers)
  }

  test("insert and retrieve book") {
    withContainersIO { case container: PostgreSQLContainer =>
      val io =
        withDb(container.jdbcUrl)(db =>
          for {
            _ <- UIO.unit
            service: LibraryService = new LibraryServiceImpl(
              profile,
              new LibraryDbio(profile),
              db
            )
            id <- service.insertAuthor(NewAuthor("author1"))
            book <- service.insertBook(NewBook("blah", "Segehwe", id))
            _ <- service
              .getBookById(book.bookId)
              .flatTap(r => UIO(println(r)))
              .assertEquals(Some(book))
          } yield ()
        )
      io
    }
  }

  test("author does not exist error on book insertion") {
    withContainersIO { case container: PostgreSQLContainer =>
      val io =
        withDb(container.jdbcUrl)(db =>
          for {
            _ <- UIO.unit
            service: LibraryService = new LibraryServiceImpl(
              profile,
              new LibraryDbio(profile),
              db
            )
            _ <- service
              .insertBook(NewBook("blah2", "agege", 23))
              .attempt
              .assertEquals(
                Left(
                  LibraryService
                    .EntityDoesNotExist("Author with id=23 does not exist")
                )
              )
          } yield ()
        )
      io
    }
  }

  test("books with isbn already exists error on book insertion") {
    withContainersIO { case container: PostgreSQLContainer =>
      val io =
        withDb(container.jdbcUrl)(db =>
          for {
            _ <- UIO.unit
            service: LibraryService = new LibraryServiceImpl(
              profile,
              new LibraryDbio(profile),
              db
            )
            _ <- service.insertBook(NewBook("blah2", "agege", 1))
            _ <- service
              .insertBook(NewBook("blah3", "agege", 1))
              .attempt
              .assertEquals(
                Left(
                  LibraryService
                    .EntityAlreadyExists("Book with isbn=agege already exists")
                )
              )
          } yield ()
        )
      io
    }
  }

  test("search books by author id") {
    withContainersIO { case container: PostgreSQLContainer =>
      val io =
        withDb(container.jdbcUrl)(db =>
          for {
            _ <- UIO.unit
            service: LibraryService = new LibraryServiceImpl(
              profile,
              new LibraryDbio(profile),
              db
            )
            id <- service.insertAuthor(NewAuthor("bar"))
            book1 <- service.insertBook(NewBook("blah3", "aeaega", id))
            book2 <- service.insertBook(NewBook("blah4", "afgegg", id))
            _ <- service
              .searchBook(BookSearchMode.AuthorName, id.toString)
              .toListL
              .toIO
              .attempt
              .assertEquals(Right(List(book1, book2)))
          } yield ()
        )
      io
    }
  }

}
