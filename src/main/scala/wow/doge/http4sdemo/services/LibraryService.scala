package wow.doge.http4sdemo.services

import cats.syntax.all._
import io.odin.Logger
import io.scalaland.chimney.cats._
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Author
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.NewAuthor
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.RawNewAuthor
import wow.doge.http4sdemo.models.RawNewBook
import wow.doge.http4sdemo.models.Refinements._
import wow.doge.http4sdemo.slickcodegen.Tables

object LibraryService {
  // import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
  // sealed trait AppError extends Exception {
  //   def message: String
  //   override def getMessage(): String = message
  //   def toResponse = {
  //     val dsl = Http4sDsl[Task]
  //     import dsl._
  //     implicit val codec = AppError.codec
  //     this match {
  //       case e @ LibraryService.EntityDoesNotExist(message) =>
  //         NotFound(e: LibraryService.AppError).hideErrors
  //       case e @ LibraryService.EntityAlreadyExists(message) =>
  //         BadRequest(e: LibraryService.AppError).hideErrors
  //     }
  //   }
  // }
  // final case class EntityDoesNotExist(message: String) extends AppError
  // final case class EntityAlreadyExists(message: String) extends AppError

  // object AppError {
  //   implicit val codec = deriveCodec[AppError]
  //   // def convert(e: MessageBodyFailure) =  e match {
  //   //   case InvalidMessageBodyFailure(details, cause) => ()
  //   //   case MalformedMessageBodyFailure(details, cause) => ()
  //   // }
  // }
}

trait LibraryService {

  def getBooks: Observable[Book]

  def getBookById(id: BookId): UIO[Option[Book]]

  def searchBook(
      mode: BookSearchMode,
      value: StringRefinement
  ): Observable[Book]

  def updateBook(id: BookId, updateData: BookUpdate): IO[AppError, Int]

  def deleteBook(id: BookId): Task[Int]

  def insertBook(newBook: NewBook): IO[AppError, Book]

  def insertAuthor(a: NewAuthor): Task[Int]

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
    .streamO(dbio.getBooks.transactionally)
    .mapEval(_.transformL[Book].toTask)

  def getBookById(id: BookId) =
    db.runL(dbio.getBook(id))
      .flatMap(_.traverse(_.transformL[Book]))
      .hideErrors

  def searchBook(
      mode: BookSearchMode,
      value: StringRefinement
  ): Observable[Book] =
    mode match {
      case BookSearchMode.BookTitle =>
        db.streamO(dbio.getBooksByTitle(BookTitle(value)).transactionally)
          .mapEval(_.transformL[Book].toTask)

      case BookSearchMode.AuthorName =>
        for {
          author <- db
            .streamO(dbio.getAuthorsByName(AuthorName(value)).transactionally)
            .mapEval(_.transformL[Author].toTask)
          book <- db
            .streamO(dbio.getBooksForAuthor(author.authorId).transactionally)
            .mapEval(_.transformL[Book].toTask)
        } yield book
    }

  def insertAuthor(a: NewAuthor): Task[Int] =
    db.runL(dbio.insertAuthor(a))

  def updateBook(id: BookId, updateData: BookUpdate): IO[AppError, Int] =
    for {
      _ <- logger.debugU(s"Request for updating book $id")
      _ <- logger.debugU(s"Value to be updated with -> $updateData")
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
    } yield rows

  def deleteBook(id: BookId) = db.runL(dbio.deleteBook(id))

  def insertBook(newBook: NewBook): IO[AppError, Book] =
    IO.deferAction { implicit s =>
      for {
        action <- UIO(for {
          _ <- dbio
            .selectBookByIsbn(newBook.isbn)
            .result
            .headOption
            .flatMap {
              case None => DBIO.unit
              case Some(_) =>
                DBIO.failed(
                  AppError.EntityAlreadyExists(
                    s"Book with isbn=${newBook.isbn} already exists"
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
          .flatMap(_.transformL[Book])
      } yield book
    }

  def booksForAuthor(authorId: AuthorId) =
    db.streamO(dbio.getBooksForAuthor(authorId).transactionally)
      .mapEval(_.transformL[Book].toTask)

}

//ATM this is the last app layer before the database itself. So this is
//where we convert our newtypes/refinements to primitives. The methods
//still take refinements as arguments, conversion is done internally.
final class LibraryDbio(val profile: JdbcProfile) {
  import profile.api._

  /*  */

  def getBooks: StreamingDBIO[Seq[Tables.BooksRow], Tables.BooksRow] =
    Tables.Books.result

  def insertBookAndGetId(newBook: NewBook): DBIO[Int] =
    Query.insertBookGetId += newBook.transformInto[RawNewBook]

  def insertBookAndGetBook(newBook: NewBook): DBIO[Tables.BooksRow] =
    Query.insertBookGetBook += newBook.transformInto[RawNewBook]

  def insertAuthor(newAuthor: NewAuthor): DBIO[Int] =
    Query.insertAuthorGetId += newAuthor.transformInto[RawNewAuthor]

  def selectBook(id: BookId) =
    Tables.Books.filter(_.bookId === id.id.value)

  def getAuthor(id: AuthorId) =
    Query.selectAuthor(id).result.headOption

  def getAuthorsByName(name: AuthorName) =
    Tables.Authors
      .filter(_.authorName === name.name.value)
      .result

  def deleteBook(id: BookId) = selectBook(id).delete

  def getBook(id: BookId) = selectBook(id).result.headOption

  def selectBookByIsbn(isbn: BookIsbn) =
    Tables.Books.filter(_.isbn === isbn.inner.value)

  def getBooksByTitle(title: BookTitle) =
    Tables.Books.filter(_.bookTitle === title.title.value).result

  def getBooksForAuthor(authorId: AuthorId) =
    Query.booksForAuthorInner(authorId).result

  private object Query {

    // val getBooksInner = Book.fromBooksTable

    val insertBookGetId =
      RawNewBook.fromBooksTable.returning(Tables.Books.map(_.bookId))

    //does not work for sqlite, works for postgres
    val insertBookGetBook =
      RawNewBook.fromBooksTable.returning(Tables.Books)

    val insertAuthorGetId =
      Tables.Authors
        .map(a => (a.authorName).mapTo[RawNewAuthor])
        .returning(Tables.Authors.map(_.authorId))

    // val insertAuthor = NewAuthor.fromAuthorsTable

    def booksForAuthorInner(authorId: AuthorId) = for {
      b <- Tables.Books
      a <- Tables.Authors
      if b.authorId === a.authorId && b.authorId === authorId.id.value
    } yield b

    def selectAuthor(authorId: AuthorId) =
      Tables.Authors.filter(_.authorId === authorId.id.value)
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

  def updateBook(
      id: BookId,
      updateData: BookUpdate
  ): IO[AppError, Int] = IO.terminate(new NotImplementedError)

  def deleteBook(id: BookId): Task[Int] = IO.terminate(new NotImplementedError)

  def insertBook(newBook: NewBook): IO[AppError, Book] =
    IO.terminate(new NotImplementedError)

  def insertAuthor(a: NewAuthor): Task[Int] =
    IO.terminate(new NotImplementedError)

  def booksForAuthor(authorId: AuthorId): Observable[Book] =
    Observable.raiseError(new NotImplementedError)

}
