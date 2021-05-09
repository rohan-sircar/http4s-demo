package wow.doge.http4sdemo

import com.dimafeng.testcontainers.PostgreSQLContainer
import monix.bio.UIO
import wow.doge.http4sdemo.dto.BookSearchMode
import wow.doge.http4sdemo.dto.BookUpdate
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

  test("retrieve book by id should succeed") {
    loggerInterceptor { logger =>
      withContainersIO { case container: PostgreSQLContainer =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              service: LibraryService = new LibraryServiceImpl(
                profile,
                new LibraryDbio(profile),
                db,
                logger
              )
              id <- service.insertAuthor(NewAuthor("author1"))
              book <- service.insertBook(NewBook("blah", "Segehwe", id))
              _ <- service
                .getBookById(book.bookId)
                .assertEquals(Some(book))
            } yield ()
          )
        io
      }
    }
  }

  test(
    "insert book should fail gracefully when provided author does not exist"
  ) {
    loggerInterceptor { logger =>
      withContainersIO { case container: PostgreSQLContainer =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              service: LibraryService = new LibraryServiceImpl(
                profile,
                new LibraryDbio(profile),
                db,
                logger
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
  }

  test(
    "insert book should fail gracefully when book with isbn already exists"
  ) {
    loggerInterceptor { logger =>
      withContainersIO { case container: PostgreSQLContainer =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              service: LibraryService = new LibraryServiceImpl(
                profile,
                new LibraryDbio(profile),
                db,
                logger
              )
              _ <- service.insertBook(NewBook("blah2", "agege", 1))
              _ <- service
                .insertBook(NewBook("blah3", "agege", 1))
                .attempt
                .assertEquals(
                  Left(
                    LibraryService
                      .EntityAlreadyExists(
                        "Book with isbn=agege already exists"
                      )
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

  test("search books by author name should succeed") {
    loggerInterceptor { logger =>
      withContainersIO { case container: PostgreSQLContainer =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              service: LibraryService = new LibraryServiceImpl(
                profile,
                new LibraryDbio(profile),
                db,
                logger
              )
              id <- service.insertAuthor(NewAuthor("bar"))
              book1 <- service.insertBook(NewBook("blah3", "aeaega", id))
              book2 <- service.insertBook(NewBook("blah4", "afgegg", id))
              _ <- service
                .searchBook(BookSearchMode.AuthorName, "bar")
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

  test("search books by book title should succeed") {
    loggerInterceptor { logger =>
      withContainersIO { case container: PostgreSQLContainer =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              service: LibraryService = new LibraryServiceImpl(
                profile,
                new LibraryDbio(profile),
                db,
                logger
              )
              id <- service.insertAuthor(NewAuthor("bar2"))
              book1 <- service.insertBook(NewBook("blah5", "aswegq", id))
              book2 <- service.insertBook(NewBook("blah6", "aaeqaf", id))
              _ <- service
                .searchBook(BookSearchMode.BookTitle, "blah5")
                .toListL
                .toIO
                .attempt
                .assertEquals(Right(List(book1)))
            } yield ()
          )
        io
      }
    }
  }

  test("update book should succeed") {
    loggerInterceptor { logger =>
      withContainersIO { case container: PostgreSQLContainer =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              service: LibraryService = new LibraryServiceImpl(
                profile,
                new LibraryDbio(profile),
                db,
                logger
              )
              id <- service.insertAuthor(NewAuthor("bar3"))
              book1 <- service.insertBook(NewBook("blah7", "fwefq3f", id))
              _ <- service
                .updateBook(book1.bookId, BookUpdate(Some("bar7"), None))
                .attempt
                .assertEquals(Right(1))
              _ <- service
                .getBookById(book1.bookId)
                .assertEquals(Some(book1.copy(bookTitle = "bar7")))
            } yield ()
          )
        io
      }
    }
  }

  test(
    "update book should fail gracefully if book with given id doesn't exist"
  ) {
    loggerInterceptor { logger =>
      withContainersIO { case container: PostgreSQLContainer =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              service: LibraryService = new LibraryServiceImpl(
                profile,
                new LibraryDbio(profile),
                db,
                logger
              )
              id <- service.insertAuthor(NewAuthor("bar4"))
              book1 <- service.insertBook(NewBook("blah7", "aegqweg", id))
              _ <- service
                .updateBook(12414, BookUpdate(Some("bar7"), None))
                .attempt
                .assertEquals(
                  Left(
                    LibraryService
                      .EntityDoesNotExist("Book with id=12414 does not exist")
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

}
