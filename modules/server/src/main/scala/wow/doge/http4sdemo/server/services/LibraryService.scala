package wow.doge.http4sdemo.server.services

import cats.syntax.all._
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.execution.Scheduler
import monix.reactive.Observable
import slick.jdbc.JdbcBackend
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Author
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.BookUpdateRow
import wow.doge.http4sdemo.models.Extra
import wow.doge.http4sdemo.models.NewAuthor
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.NewExtra
import wow.doge.http4sdemo.models.common.Color
import wow.doge.http4sdemo.models.pagination.Pagination
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.refinements._
import wow.doge.http4sdemo.server.ExtendedPgProfile.api._
import wow.doge.http4sdemo.server.ExtendedPgProfile.mapping._
import wow.doge.http4sdemo.server.implicits._
import wow.doge.http4sdemo.server.repos.BookImagesRepo
import wow.doge.http4sdemo.server.utils.ImageStream
import wow.doge.http4sdemo.slickcodegen.Tables
import wow.doge.http4sdemo.utils.infoSpan

trait LibraryService {

  def getBooks(pagination: Pagination)(implicit
      logger: Logger[Task]
  ): IO[AppError, Observable[Book]]

  def getBookById(id: BookId)(implicit logger: Logger[Task]): UIO[Option[Book]]

  def searchBooks(
      mode: BookSearchMode,
      value: SearchQuery
  )(implicit logger: Logger[Task]): IO[AppError, Observable[Book]]

  def updateBook(id: BookId, updateData: BookUpdate)(implicit
      logger: Logger[Task]
  ): IO[AppError, NumRows]

  def deleteBook(id: BookId)(implicit
      logger: Logger[Task]
  ): IO[AppError, NumRows]

  def createBook(newBook: NewBook)(implicit
      logger: Logger[Task]
  ): IO[AppError, Book]

  def createBooks(newBooks: List[NewBook])(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[NumRows]]

  def createAuthor(a: NewAuthor)(implicit
      logger: Logger[Task]
  ): IO[AppError, AuthorId]

  def getBooksByAuthorId(authorId: AuthorId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Observable[Book]]

  def searchExtras(query: String)(implicit
      logger: Logger[Task]
  ): Observable[Extra]

  def createExtra(ne: NewExtra)(implicit logger: Logger[Task]): Task[Int]

  def uploadBookImage(id: BookId, imageStream: ImageStream)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit]

  def downloadBookImage(id: BookId)(implicit
      logger: Logger[Task]
  ): IO[AppError, ImageStream]

}

final class LibraryServiceImpl(
    db: JdbcBackend.DatabaseDef,
    dbio: LibraryDbio,
    bookImagesRepo: BookImagesRepo
) extends LibraryService {

  def getBooks(pagination: Pagination)(implicit
      logger: Logger[Task]
  ) =
    infoSpan(UIO(db.streamO(dbio.getPaginatedBooks(pagination))))

  def getBookById(id: BookId)(implicit
      logger: Logger[Task]
  ) =
    infoSpan { db.runL(dbio.getBook(id)).hideErrors }

  def searchBooks(
      mode: BookSearchMode,
      query: SearchQuery
  )(implicit
      logger: Logger[Task]
  ): IO[AppError, Observable[Book]] = infoSpan {
    for {
      s <- s"%${query.value}%".transformL[SearchQuery]
      _ <- logger.debugU(s"Query str value = $s")
      res = mode match {
        case BookSearchMode.BookTitle =>
          db.streamO(dbio.searchBooks(query))
        case BookSearchMode.AuthorName =>
          for {
            author <- db.streamO(dbio.searchAuthors(s))
            book <- db.streamO(dbio.getBooksForAuthor(author.authorId))
          } yield book
      }
    } yield res
  }

  def createAuthor(a: NewAuthor)(implicit
      logger: Logger[Task]
  ): IO[AppError, AuthorId] = db.runIO(dbio.insertAuthor(a))

  def updateBook(id: BookId, updateData: BookUpdate)(implicit
      logger: Logger[Task]
  ): IO[AppError, NumRows] =
    infoSpan {
      for {
        _ <- logger.debugU(s"Request for updating book $id")
        action <- UIO.deferAction(implicit s =>
          UIO(for {
            mbRow <- dbio.getBookUpdateRowById(id).result.headOption
            updatedRow <- mbRow match {
              case Some(value) =>
                DBIO.fromIO(logger.debug(s"Original value -> $value")) >>
                  DBIO.successful(updateData.update(value))
              case None =>
                DBIO.failed(
                  AppError.EntityDoesNotExist(
                    s"Book with id=$id does not exist"
                  )
                )
            }
            updateAction = dbio.getBookUpdateRowById(id).update(updatedRow)
            _ <- DBIO.fromIO(logger.trace(s"SQL = ${updateAction.statements}"))
            res <- updateAction
          } yield res)
        )
        rows <- db.runIO(action.transactionally)
      } yield NumRows(rows)
    }

  def deleteBook(id: BookId)(implicit
      logger: Logger[Task]
  ) = db.runIO(dbio.deleteBook(id)).map(NumRows.apply)

  def createBook(newBook: NewBook)(implicit
      logger: Logger[Task]
  ): IO[AppError, Book] =
    infoSpan {
      IO.deferAction { implicit s =>
        for {
          action <- UIO(for {
            _ <- dbio.checkBookIsbnExists(newBook.bookIsbn)
            _ <- dbio.checkAuthorWithIdExists(newBook.authorId)
            book <- dbio.insertBookAndGetBook(newBook)
          } yield book)
          book <- db.runIO(action.transactionally)
        } yield book
      }
    }

  def createBooks(newBooks: List[NewBook])(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[NumRows]] =
    infoSpan {
      for {
        l <- IO.pure(LazyList.from(newBooks))
        _ <-
          if (l.length === l.distinctBy(_.bookIsbn.value).length)
            IO.unit
          else
            IO.raiseError(
              new AppError.BadInput(s"Duplicate isbns provided")
            )
        action <- IO.deferAction(implicit s =>
          UIO(
            for {
              //try fetching books with given isbns
              _ <- for {
                l2 <- DBIO
                  .traverse(l)(nb =>
                    dbio
                      .selectBookByIsbn(nb.bookIsbn)
                      .map(_.bookIsbn)
                      .result
                      .headOption
                  )
                  .map(_.mapFilter(identity).toList)
                //if the list of isbns from above is not empty, return an error
                //since it means that these isbns already exist
                _ <-
                  if (l2.isEmpty) DBIO.unit
                  else
                    DBIO.failed(
                      AppError.EntityAlreadyExists(
                        s"Books with these isbns already exist: $l2"
                      )
                    )
              } yield ()
              _ <- for {
                l3 <-
                  DBIO
                    .traverse(l)(nb =>
                      dbio
                        .getAuthor(nb.authorId)
                        .map(o => nb.authorId -> o.map(_.authorId))
                    )
                    .map(_.foldLeft(List.empty[AuthorId]) {
                      case (acc, (_, Some(_))) => acc
                      case (acc, (id, None))   => id :: acc
                    })

                _ <-
                  if (l3.isEmpty) DBIO.unit
                  else
                    DBIO.failed(
                      AppError.EntityDoesNotExist(
                        s"Authors with these ids do not exist: $l3"
                      )
                    )
              } yield ()
              rows <- dbio.insertBooks(newBooks)
            } yield rows.map(NumRows.apply)
          )
        )
        res <- db.runIO(action.transactionally)
      } yield res
    }

  def getBooksByAuthorId(authorId: AuthorId)(implicit
      logger: Logger[Task]
  ) =
    infoSpan {
      for {
        action <- UIO.deferAction(implicit s =>
          UIO(for {
            _ <- dbio.checkAuthorWithIdExists(authorId)
          } yield ())
        )
        _ <- db.runIO(action.transactionally)
        o = db.streamO(dbio.getBooksForAuthor(authorId))
      } yield o
    }

  def extrasRow = db.streamO(dbio.extrasRows)

  def createExtra(ne: NewExtra)(implicit
      logger: Logger[Task]
  ) = db
    .runL(dbio.insertExtra(ne))
    .tapError(err => logger.error("DB error", err))

  def searchExtras(query: String)(implicit
      logger: Logger[Task]
  ) = db.streamO(dbio.searchExtra(query))

  def uploadBookImage(id: BookId, imageStream: ImageStream)(implicit
      logger: Logger[Task]
  ) = infoSpan {
    for {
      action <- UIO.deferAction(implicit s =>
        UIO(for {
          _ <- dbio.checkBookIdExists(id)
        } yield ())
      )
      _ <- db.runIO(action.transactionally)
      _ <- bookImagesRepo.put(id, imageStream).hideErrors
    } yield ()
  }

  def downloadBookImage(id: BookId)(implicit
      logger: Logger[Task]
  ): IO[AppError, ImageStream] = infoSpan {
    for {
      action <- UIO.deferAction(implicit s =>
        UIO(for {
          _ <- dbio.checkBookIdExists(id)
        } yield ())
      )
      _ <- db.runIO(action.transactionally)
      iStream <- bookImagesRepo.get(id)
    } yield iStream
  }

}

final class LibraryDbio {

  def insertBookAndGetId(newBook: NewBook): DBIO[BookId] =
    Query.insertBookGetId += newBook

  def insertBookAndGetBook(newBook: NewBook): DBIO[Book] =
    Query.insertBookGetBook += newBook

  def insertBooks(newBooks: Iterable[NewBook]): DBIO[Option[Int]] =
    (Tables.Books.map(NewBook.fromBooksTableFn) ++= newBooks)

  def insertAuthor(newAuthor: NewAuthor): DBIO[AuthorId] =
    Query.insertAuthorGetId += newAuthor

  def selectBook(id: BookId) =
    Tables.Books.filter(_.bookId === id)

  def getBookUpdateRowById(id: BookId) =
    selectBook(id)
      .map(b => (b.bookTitle, b.authorId).mapTo[BookUpdateRow])

  def getAuthor(id: AuthorId) =
    Query.selectAuthor(id).map(Author.fromAuthorsTableFn).result.headOption

  def getAuthorsByName(name: AuthorName) =
    Tables.Authors
      .filter(_.authorName === name)
      .result

  def deleteBook(id: BookId) = selectBook(id).delete

  def getBook(id: BookId) = selectBook(id)
    .map(Book.fromBooksTableFn)
    .result
    .headOption

  def getPaginatedBooks(pagination: Pagination) = {
    Tables.Books
      .sortBy(_.createdAt)
      .drop(pagination.offset.toInt)
      .take(pagination.limit.inner.value)
      .map(Book.fromBooksTableFn)
      .result
  }

  def selectBookByIsbn(isbn: BookIsbn) =
    Tables.Books.filter(_.bookIsbn === isbn)

  def getBooksByTitle(title: BookTitle) =
    Tables.Books.filter(_.bookTitle === title).result

  def getBooksForAuthor(authorId: AuthorId) =
    Query.booksForAuthorInner(authorId).map(Book.fromBooksTableFn).result

  def searchBooks(query: SearchQuery) =
    Tables.Books
      .filter(_.tsv @@ toTsQuery(query.value.bind))
      .map(Book.fromBooksTableFn)
      .result

  def searchAuthors(query: SearchQuery) =
    Tables.Authors
      .filter(_.authorName.asColumnOf[String].ilike(query.value))
      .result

  Tables.Extras.filter(_.color === Color.blue)

  val extrasRows = Tables.Extras.map(Extra.fromExtrasTableFn).result

  def insertExtra(newExtra: NewExtra) = Tables.Extras
    .map(NewExtra.fromExtrasTableFn)
    .returning(Tables.Extras.map(_.extrasId)) += newExtra

  def searchExtra(query: String) = {
    val q = toTsQuery(query.bind)
    Tables.Extras
      .filter(_.tsv @@ q)
      .map(Extra.fromExtrasTableFn)
      .result
  }

  def checkBookIdExists(bookId: BookId)(implicit S: Scheduler) = {
    selectBook(bookId)
      .map(_.bookId)
      .result
      .headOption
      .flatMap {
        case None =>
          DBIO.failed(
            AppError.EntityDoesNotExist(
              s"Book with id=${bookId} does not exist"
            )
          )

        case Some(_) => DBIO.unit

      }
  }

  def checkBookIsbnExists(bookIsbn: BookIsbn)(implicit S: Scheduler) = {
    selectBookByIsbn(bookIsbn)
      .map(_.bookId)
      .result
      .headOption
      .flatMap {
        case None => DBIO.unit
        case Some(_) =>
          DBIO.failed(
            AppError.EntityAlreadyExists(
              s"Book with isbn=${bookIsbn} already exists"
            )
          )
      }
  }

  def checkAuthorWithIdExists(id: AuthorId)(implicit S: Scheduler) = {
    getAuthor(id).flatMap {
      case None =>
        DBIO.failed(
          AppError.EntityDoesNotExist(
            s"Author with id=$id does not exist"
          )
        )
      case Some(_) => DBIO.unit
    }
  }

  private object Query {

    val insertBookGetId =
      Tables.Books
        .map(NewBook.fromBooksTableFn)
        .returning(Tables.Books.map(_.bookId))

    //does not work for sqlite, works for postgres
    val insertBookGetBook =
      Tables.Books
        .map(NewBook.fromBooksTableFn)
        .returning(Tables.Books.map(Book.fromBooksTableFn))

    val insertAuthorGetId =
      Tables.Authors
        .map(a => (a.authorName).mapTo[NewAuthor])
        .returning(Tables.Authors.map(_.authorId))

    // val insertAuthor = NewAuthor.fromAuthorsTable

    def booksForAuthorInner(authorId: AuthorId) = for {
      b <- Tables.Books
      a <- Tables.Authors
      if b.authorId === a.authorId && b.authorId === authorId
    } yield b

    def selectAuthor(authorId: AuthorId) =
      Tables.Authors.filter(_.authorId === authorId)
  }
}

class NoopLibraryService extends LibraryService {

  def getBooks(pagination: Pagination)(implicit
      L: Logger[Task]
  ): IO[AppError, Observable[Book]] =
    IO.terminate(new NotImplementedError)

  def getBookById(id: BookId)(implicit L: Logger[Task]): UIO[Option[Book]] =
    IO.terminate(new NotImplementedError)

  def searchBooks(mode: BookSearchMode, query: SearchQuery)(implicit
      L: Logger[Task]
  ): IO[AppError, Observable[Book]] =
    IO.terminate(new NotImplementedError)

  def updateBook(id: BookId, updateData: BookUpdate)(implicit
      L: Logger[Task]
  ): IO[AppError, NumRows] =
    IO.terminate(new NotImplementedError)

  def deleteBook(id: BookId)(implicit L: Logger[Task]): IO[AppError, NumRows] =
    IO.terminate(new NotImplementedError)

  def createBook(newBook: NewBook)(implicit
      L: Logger[Task]
  ): IO[AppError, Book] =
    IO.terminate(new NotImplementedError)

  def createBooks(newBooks: List[NewBook])(implicit
      L: Logger[Task]
  ): IO[AppError, Option[NumRows]] =
    IO.terminate(new NotImplementedError)

  def createAuthor(a: NewAuthor)(implicit
      L: Logger[Task]
  ): IO[AppError, AuthorId] =
    IO.terminate(new NotImplementedError)

  def getBooksByAuthorId(authorId: AuthorId)(implicit
      L: Logger[Task]
  ): IO[AppError, Observable[Book]] =
    IO.terminate(new NotImplementedError)

  def searchExtras(query: String)(implicit L: Logger[Task]): Observable[Extra] =
    Observable.raiseError(new NotImplementedError)

  def createExtra(ne: NewExtra)(implicit L: Logger[Task]): Task[Int] =
    IO.terminate(new NotImplementedError)

  def uploadBookImage(id: BookId, imageStream: ImageStream)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = IO.terminate(new NotImplementedError)

  def downloadBookImage(id: BookId)(implicit
      logger: Logger[Task]
  ): IO[AppError, ImageStream] = IO.terminate(new NotImplementedError)

}
