package wow.doge.http4sdemo.services

import cats.syntax.all._
import io.odin.Logger
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import slick.jdbc.JdbcBackend
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Author
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.NewAuthor
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.Refinements._
import wow.doge.http4sdemo.models.pagination.Pagination
import wow.doge.http4sdemo.profile.{ExtendedPgProfile => JdbcProfile}
import wow.doge.http4sdemo.slickcodegen.Tables

trait LibraryService {

  def getBooks: Observable[Book]

  def getBookById(id: BookId): UIO[Option[Book]]

  def searchBook(
      mode: BookSearchMode,
      value: StringRefinement
  ): Observable[Book]

  def getPaginatedBooks(pagination: Pagination): Observable[Book]

  def updateBook(id: BookId, updateData: BookUpdate): IO[AppError, NumRows]

  def deleteBook(id: BookId): Task[NumRows]

  def insertBook(newBook: NewBook): IO[AppError, Book]

  def insertAuthor(a: NewAuthor): Task[AuthorId]

  def booksForAuthor(authorId: AuthorId): Observable[Book]

}

final class LibraryServiceImpl(
    profile: JdbcProfile,
    dbio: LibraryDbio,
    db: JdbcBackend.DatabaseDef,
    logger: Logger[Task]
) extends LibraryService {
  import profile.api._

  def getBooks = db
    .streamO(dbio.getBooks)
    .map(_.transformInto[Book])

  def getBookById(id: BookId) =
    db.runL(dbio.getBook(id))
      .flatMap(_.traverse(b => IO.pure(b.transformInto[Book])))
      .hideErrors

  def searchBook(
      mode: BookSearchMode,
      value: StringRefinement
  ): Observable[Book] =
    mode match {
      case BookSearchMode.BookTitle =>
        db.streamO(dbio.getBooksByTitle(BookTitle(value)))
          .map(_.transformInto[Book])

      case BookSearchMode.AuthorName =>
        for {
          author <- db
            .streamO(dbio.getAuthorsByName(AuthorName(value)))
            .map(_.transformInto[Author])
          book <- db
            .streamO(dbio.getBooksForAuthor(author.authorId))
            .map(_.transformInto[Book])
        } yield book
    }

  def getPaginatedBooks(pagination: Pagination) = db
    .streamO(dbio.getPaginatedBooks(pagination))
    .map(_.transformInto[Book])

  def insertAuthor(a: NewAuthor): Task[AuthorId] =
    db.runL(dbio.insertAuthor(a))

  def updateBook(id: BookId, updateData: BookUpdate): IO[AppError, NumRows] =
    for {
      _ <- logger.debugU(s"Request for updating book $id")
      action <- UIO.deferAction(implicit s =>
        UIO(for {
          mbRow <- dbio.selectBook(id).result.headOption
          updatedRow <- mbRow match {
            case Some(value) =>
              DBIO.fromIO(logger.debug(s"Original value -> $value")) >>
                DBIO.successful(updateData.update(value))
            case None =>
              DBIO.failed(
                AppError.EntityDoesNotExist(s"Book with id=$id does not exist")
              )
          }
          updateAction = dbio.selectBook(id).update(updatedRow)
          _ <- DBIO.fromIO(logger.trace(s"SQL = ${updateAction.statements}"))
          res <- updateAction
        } yield res)
      )
      rows <- db
        .runTryL(action.transactionally.asTry)
        .mapErrorPartial { case e: AppError =>
          e
        }
    } yield NumRows(rows)

  def deleteBook(id: BookId) = db.runL(dbio.deleteBook(id)).map(NumRows.apply)

  def insertBook(newBook: NewBook): IO[AppError, Book] =
    IO.deferAction { implicit s =>
      for {
        action <- UIO(for {
          _ <- dbio
            .selectBookByIsbn(newBook.bookIsbn)
            .result
            .headOption
            .flatMap {
              case None => DBIO.unit
              case Some(_) =>
                DBIO.failed(
                  AppError.EntityAlreadyExists(
                    s"Book with isbn=${newBook.bookIsbn} already exists"
                  )
                )
            }
          _ <- dbio.getAuthor(newBook.authorId).flatMap {
            case None =>
              DBIO.failed(
                AppError.EntityDoesNotExist(
                  s"Author with id=${newBook.authorId} does not exist"
                )
              )
            case Some(_) => DBIO.unit
          }
          book <- dbio.insertBookAndGetBook(newBook)
        } yield book)
        book <- db
          .runTryL(action.transactionally.asTry)
          .mapErrorPartial { case e: AppError =>
            e
          }
          .flatMap(b => IO.pure(b.transformInto[Book]))
      } yield book
    }

  def booksForAuthor(authorId: AuthorId) =
    db.streamO(dbio.getBooksForAuthor(authorId).transactionally)
      .map(_.transformInto[Book])

}

final class LibraryDbio(val profile: JdbcProfile) {
  import profile.api._

  /*  */

  def getBooks: StreamingDBIO[Seq[Tables.BooksRow], Tables.BooksRow] =
    Tables.Books.result

  def insertBookAndGetId(newBook: NewBook): DBIO[BookId] =
    Query.insertBookGetId += newBook

  def insertBookAndGetBook(newBook: NewBook): DBIO[Tables.BooksRow] =
    Query.insertBookGetBook += newBook

  def insertAuthor(newAuthor: NewAuthor): DBIO[AuthorId] =
    Query.insertAuthorGetId += newAuthor

  def selectBook(id: BookId) =
    Tables.Books.filter(_.bookId === id)

  def getAuthor(id: AuthorId) =
    Query.selectAuthor(id).result.headOption

  def getAuthorsByName(name: AuthorName) =
    Tables.Authors
      .filter(_.authorName === name)
      .result

  def deleteBook(id: BookId) = selectBook(id).delete

  def getBook(id: BookId) = selectBook(id).result.headOption

  def getPaginatedBooks(pagination: Pagination) = {
    Tables.Books
      .sortBy(_.createdAt)
      .drop(pagination.offset.toInt)
      .take(pagination.limit.inner.value)
      .result
  }

  def selectBookByIsbn(isbn: BookIsbn) =
    Tables.Books.filter(_.bookIsbn === isbn)

  def getBooksByTitle(title: BookTitle) =
    Tables.Books.filter(_.bookTitle === title).result

  def getBooksForAuthor(authorId: AuthorId) =
    Query.booksForAuthorInner(authorId).result

  private object Query {

    // val getBooksInner = Book.fromBooksTable

    val insertBookGetId =
      NewBook.fromBooksTable.returning(Tables.Books.map(_.bookId))

    //does not work for sqlite, works for postgres
    val insertBookGetBook =
      NewBook.fromBooksTable.returning(Tables.Books)

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

trait NoopLibraryService extends LibraryService {
  def getBooks: Observable[Book] =
    Observable.raiseError(new NotImplementedError)

  def getBookById(id: BookId): UIO[Option[Book]] =
    IO.terminate(new NotImplementedError)

  def searchBook(
      mode: BookSearchMode,
      value: StringRefinement
  ): Observable[Book] = Observable.raiseError(new NotImplementedError)

  def getPaginatedBooks(pagination: Pagination): Observable[Book] =
    Observable.raiseError(new NotImplementedError)

  def updateBook(
      id: BookId,
      updateData: BookUpdate
  ): IO[AppError, NumRows] = IO.terminate(new NotImplementedError)

  def deleteBook(id: BookId): Task[NumRows] =
    IO.terminate(new NotImplementedError)

  def insertBook(newBook: NewBook): IO[AppError, Book] =
    IO.terminate(new NotImplementedError)

  def insertAuthor(a: NewAuthor): Task[AuthorId] =
    IO.terminate(new NotImplementedError)

  def booksForAuthor(authorId: AuthorId): Observable[Book] =
    Observable.raiseError(new NotImplementedError)

}
