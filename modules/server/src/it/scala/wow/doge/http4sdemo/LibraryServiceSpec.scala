package wow.doge.http4sdemo

import com.dimafeng.testcontainers.PostgreSQLContainer
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric
import fs2.Chunk
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.NewAuthor
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.repos.NoopBookImagesRepo
import wow.doge.http4sdemo.server.services.LibraryDbio
import wow.doge.http4sdemo.server.services.LibraryService
import wow.doge.http4sdemo.server.services.LibraryServiceImpl
import wow.doge.http4sdemo.server.utils.ImageStream

final class LibraryServiceSpec extends PgItTestBase {

  override def afterContainersStart(containers: Containers): Unit = {
    implicit val s = schedulers.io.value
    super.afterContainersStart(containers)
    val io = containers match {
      case container: PostgreSQLContainer =>
        createSchema(container)
      case c => IO.terminate(new Exception("boom"))
    }
    io.runSyncUnsafe(munitTimeout)
  }

  test("get book by id should succeed") {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              id <- service.createAuthor(
                NewAuthor(AuthorName("author1"))
              )
              book <- service.createBook(
                NewBook(
                  BookTitle("blahh"),
                  BookIsbn("randomisbn"),
                  id
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
    "create book should fail gracefully when provided author does not exist"
  ) {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              _ <- service
                .createBook(
                  NewBook(
                    BookTitle("blah2g"),
                    BookIsbn("agege"),
                    AuthorId(numeric.PosInt(23))
                  )
                )
                .attempt
                .assertEquals(
                  Left(
                    AppError2
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
    "create book should fail gracefully when book with isbn already exists"
  ) {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              _ <- service.createBook(
                NewBook(
                  BookTitle("blah2"),
                  BookIsbn("agege"),
                  AuthorId(numeric.PosInt(1))
                )
              )
              _ <- service
                .createBook(
                  NewBook(
                    BookTitle("blah3"),
                    BookIsbn("agege"),
                    AuthorId(numeric.PosInt(1))
                  )
                )
                .attempt
                .assertEquals(
                  Left(
                    AppError2
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
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )

              id <- service.createAuthor(
                NewAuthor(AuthorName("barbar"))
              )
              // _ = {
              //   import profile.api._
              //   db.run(
              //     Tables.Books.++=(
              //       Seq(
              //         Tables.BooksRow(
              //           BookId(1231231),
              //           BookIsbn("aeaega"),
              //           BookTitle("blah3"),
              //           id,
              //           LocalDateTime.now
              //         )
              //       )
              //     )
              //   )
              // }
              book1 <- service.createBook(
                NewBook(
                  BookTitle("blah3"),
                  BookIsbn("aeaega"),
                  id
                )
              )
              book2 <- service.createBook(
                NewBook(
                  BookTitle("blah4"),
                  BookIsbn("afgegg"),
                  id
                )
              )
              _ <- service
                .searchBooks(
                  BookSearchMode.AuthorName,
                  "barbar"
                )
                .flatMap(_.toListL.toIO)
                .attempt
                .assertEquals(Right(List(book1, book2)))
            } yield ()
          )
        io
      }
    }
  }

  test("search books by book title should succeed") {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              id <- service.createAuthor(
                NewAuthor(AuthorName("barbar2"))
              )
              book1 <- service.createBook(
                NewBook(
                  BookTitle("blah5"),
                  BookIsbn("aswegq"),
                  id
                )
              )
              book2 <- service.createBook(
                NewBook(
                  BookTitle("blah6"),
                  BookIsbn("aaeqaf"),
                  id
                )
              )
              _ <- service
                .searchBooks(BookSearchMode.BookTitle, "blah5")
                .flatMap(_.toListL.toIO)
                .attempt
                .assertEquals(Right(List(book1)))
            } yield ()
          )
        io
      }
    }
  }

  test("update book should succeed") {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              id <- service.createAuthor(
                NewAuthor(AuthorName("barbar3"))
              )
              book1 <- service.createBook(
                NewBook(BookTitle("blah7"), BookIsbn("fwefq3f"), id)
              )
              _ <- service
                .updateBook(
                  book1.bookId,
                  BookUpdate(Some(BookTitle("barbar7")), None)
                )
                .attempt
                .assertEquals(Right(NumRows(1)))
              _ <- service
                .getBookById(book1.bookId)
                .assertEquals(
                  Some(
                    book1
                      .copy(bookTitle = BookTitle("barbar7"))
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
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              id <- service.createAuthor(
                NewAuthor(AuthorName("barbar4"))
              )
              book1 <- service.createBook(
                NewBook(
                  BookTitle("blah7"),
                  BookIsbn("aegqweg"),
                  id
                )
              )
              _ <- service
                .updateBook(
                  BookId(12414),
                  BookUpdate(Some(BookTitle("barbar7")), None)
                )
                .attempt
                .assertEquals(
                  Left(
                    AppError2
                      .EntityDoesNotExist("Book with id=12414 does not exist")
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

  test(
    "create books should fail gracefully on duplicate isbn's in input"
  ) {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              id <- service.createAuthor(
                NewAuthor(AuthorName("barbar4"))
              )
              books = List(
                NewBook(
                  BookTitle("blah7"),
                  BookIsbn("aegqweg"),
                  id
                ),
                NewBook(
                  BookTitle("blah8"),
                  BookIsbn("aegqweg"),
                  id
                )
              )
              _ <- service
                .createBooks(books)
                .attempt
                .assertEquals(
                  Left(
                    AppError2.BadInput("Duplicate isbns provided")
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

  test(
    "create books should fail gracefully if any provided isbns already exist"
  ) {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              id <- service.createAuthor(
                NewAuthor(AuthorName("barbar4"))
              )
              _ <- service.createBook(
                NewBook(
                  BookTitle("blah10"),
                  BookIsbn("safasfa"),
                  id
                )
              )
              _ <- service.createBook(
                NewBook(
                  BookTitle("blah10"),
                  BookIsbn("asdasfa"),
                  id
                )
              )
              books = List(
                NewBook(
                  BookTitle("blah7"),
                  BookIsbn("asasweg"),
                  id
                ),
                NewBook(
                  BookTitle("blah8"),
                  BookIsbn("safasfa"),
                  id
                ),
                NewBook(
                  BookTitle("blah10"),
                  BookIsbn("asdasfa"),
                  id
                )
              )
              _ <- service
                .createBooks(books)
                .attempt
                .assertEquals(
                  Left(
                    AppError2
                      .EntityAlreadyExists(
                        "Books with these isbns already exist: List(safasfa, asdasfa)"
                      )
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

  test(
    "create books should fail gracefully if any provided author id's don't exist"
  ) {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              id <- service.createAuthor(
                NewAuthor(AuthorName("barbar4"))
              )
              books = List(
                NewBook(
                  BookTitle("blah11"),
                  BookIsbn("asasaeg"),
                  id
                ),
                NewBook(
                  BookTitle("blah12"),
                  BookIsbn("agegwvyj"),
                  AuthorId(2134)
                ),
                NewBook(
                  BookTitle("blah12"),
                  BookIsbn("turyude"),
                  AuthorId(2123)
                )
              )
              _ <- service
                .createBooks(books)
                .attempt
                .assertEquals(
                  Left(
                    AppError2
                      .EntityDoesNotExist(
                        "Authors with these ids do not exist: List(2123, 2134)"
                      )
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

  test(
    "upload book image should fail gracefully if book with id does not exist"
  ) {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              dataBytes = Chunk.array(
                os.read.bytes(os.resource / "images" / "JME.png")
              )
              iStream <- ImageStream.parse(
                fs2.Stream.chunk[Task, Byte](dataBytes)
              )
              _ <- service
                .uploadBookImage(BookId(2311), iStream)
                .attempt
                .assertEquals(
                  Left(
                    AppError2
                      .EntityDoesNotExist("Book with id=2311 does not exist")
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

  test(
    "download book image should fail gracefully if book with id does not exist"
  ) {
    withReplayLogger { implicit logger =>
      withContainersIO { container =>
        val io =
          withDb(container.jdbcUrl)(db =>
            for {
              _ <- UIO.unit
              bookImagesRepo = new NoopBookImagesRepo
              service: LibraryService = new LibraryServiceImpl(
                db,
                new LibraryDbio,
                bookImagesRepo
              )
              _ <- service
                .downloadBookImage(BookId(2311))
                .attempt
                .assertEquals(
                  Left(
                    AppError2
                      .EntityDoesNotExist("Book with id=2311 does not exist")
                  )
                )
            } yield ()
          )
        io
      }
    }
  }

}
