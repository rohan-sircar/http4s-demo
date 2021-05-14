package wow.doge.http4sdemo

import com.dimafeng.testcontainers.PostgreSQLContainer
import eu.timepit.refined.types.numeric
import monix.bio.UIO
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.NewAuthor
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.Refinements._
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.services.LibraryServiceImpl

class LibraryServiceSpec extends DatabaseIntegrationTestBase {

  override def afterContainersStart(containers: Containers): Unit = {
    super.afterContainersStart(containers)
    createSchema(containers)
  }

  test("retrieve book by id should succeedd") {
    withReplayLogger { logger =>
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
              rawId <- service.insertAuthor(
                NewAuthor(AuthorName(StringRefinement("author1")))
              )
              id <- rawId.transformL[numeric.PosInt]
              book <- service.insertBook(
                NewBook(
                  BookTitle(StringRefinement("blahh")),
                  BookIsbn(StringRefinement("randomisbn")),
                  AuthorId(id)
                )
              )
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
    withReplayLogger { logger =>
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
                .insertBook(
                  NewBook(
                    BookTitle(StringRefinement("blah2g")),
                    BookIsbn(StringRefinement("agege")),
                    AuthorId(numeric.PosInt(23))
                  )
                )
                .attempt
                .assertEquals(
                  Left(
                    AppError
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
    withReplayLogger { logger =>
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
              _ <- service.insertBook(
                NewBook(
                  BookTitle(StringRefinement("blah2")),
                  BookIsbn(StringRefinement("agege")),
                  AuthorId(numeric.PosInt(1))
                )
              )
              _ <- service
                .insertBook(
                  NewBook(
                    BookTitle(StringRefinement("blah3")),
                    BookIsbn(StringRefinement("agege")),
                    AuthorId(numeric.PosInt(1))
                  )
                )
                .attempt
                .assertEquals(
                  Left(
                    AppError
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
    withReplayLogger { logger =>
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
              rawId <- service.insertAuthor(
                NewAuthor(AuthorName(StringRefinement("barbar")))
              )
              id <- rawId.transformL[numeric.PosInt]
              book1 <- service.insertBook(
                NewBook(
                  BookTitle(StringRefinement("blah3")),
                  BookIsbn(StringRefinement("aeaega")),
                  AuthorId(id)
                )
              )
              book2 <- service.insertBook(
                NewBook(
                  BookTitle(StringRefinement("blah4")),
                  BookIsbn(StringRefinement("afgegg")),
                  AuthorId(id)
                )
              )
              _ <- service
                .searchBook(
                  BookSearchMode.AuthorName,
                  StringRefinement("barbar")
                )
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
    withReplayLogger { logger =>
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
              rawId <- service.insertAuthor(
                NewAuthor(AuthorName(StringRefinement("barbar2")))
              )
              id <- rawId.transformL[numeric.PosInt]
              book1 <- service.insertBook(
                NewBook(
                  BookTitle(StringRefinement("blah5")),
                  BookIsbn(StringRefinement("aswegq")),
                  AuthorId(id)
                )
              )
              book2 <- service.insertBook(
                NewBook(
                  BookTitle(StringRefinement("blah6")),
                  BookIsbn(StringRefinement("aaeqaf")),
                  AuthorId(id)
                )
              )
              _ <- service
                .searchBook(BookSearchMode.BookTitle, StringRefinement("blah5"))
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
    withReplayLogger { logger =>
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
              rawId <- service.insertAuthor(
                NewAuthor(AuthorName(StringRefinement("barbar3")))
              )
              id <- rawId.transformL[numeric.PosInt]
              book1 <- service.insertBook(
                NewBook(
                  BookTitle(StringRefinement("blah7")),
                  BookIsbn(StringRefinement("fwefq3f")),
                  AuthorId(id)
                )
              )
              _ <- service
                .updateBook(
                  book1.bookId,
                  BookUpdate(Some(BookTitle(StringRefinement("barbar7"))), None)
                )
                .attempt
                .assertEquals(Right(1))
              _ <- service
                .getBookById(book1.bookId)
                .assertEquals(
                  Some(
                    book1
                      .copy(bookTitle = BookTitle(StringRefinement("barbar7")))
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

  test(
    "update book should fail gracefully if book with given id doesn't exist"
  ) {
    withReplayLogger { logger =>
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
              rawId <- service.insertAuthor(
                NewAuthor(AuthorName(StringRefinement("barbar4")))
              )
              id <- rawId.transformL[numeric.PosInt]
              book1 <- service.insertBook(
                NewBook(
                  BookTitle(StringRefinement("blah7")),
                  BookIsbn(StringRefinement("aegqweg")),
                  AuthorId(id)
                )
              )
              _ <- service
                .updateBook(
                  BookId(IdRefinement(12414)),
                  BookUpdate(Some(BookTitle(StringRefinement("barbar7"))), None)
                )
                .attempt
                .assertEquals(
                  Left(
                    AppError
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
