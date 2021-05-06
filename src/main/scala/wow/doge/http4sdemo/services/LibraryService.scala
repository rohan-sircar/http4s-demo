package wow.doge.http4sdemo.services

import io.circe.generic.semiauto._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import org.http4s.dsl.Http4sDsl
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.dto.Author
import wow.doge.http4sdemo.dto.Book
import wow.doge.http4sdemo.dto.BookSearchMode
import wow.doge.http4sdemo.dto.BookSearchMode.AuthorName
import wow.doge.http4sdemo.dto.BookSearchMode.BookTitle
import wow.doge.http4sdemo.dto.BookUpdate
import wow.doge.http4sdemo.dto.NewAuthor
import wow.doge.http4sdemo.dto.NewBook
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.slickcodegen.Tables

object LibraryService {
  sealed trait Error extends Exception {
    def message: String
    override def getMessage(): String = message
    def toResponse = {
      val dsl = Http4sDsl[Task]
      import org.http4s.circe.CirceEntityCodec._
      import dsl._
      implicit val codec = Error.codec
      this match {
        case e @ LibraryService.EntityDoesNotExist(message) =>
          NotFound(e: LibraryService.Error).hideErrors
        case e @ LibraryService.EntityAlreadyExists(message) =>
          BadRequest(e: LibraryService.Error).hideErrors
      }
    }
  }
  final case class EntityDoesNotExist(message: String) extends Error
  final case class EntityAlreadyExists(message: String) extends Error
  // final case class MessageBodyError(cause: MessageBodyFailure) extends Error
  // final case class MyError2(message: String) extends Error
  // case object C3 extends Error { val message: String = "C3" }

  object Error {
    implicit val codec = deriveCodec[Error]
    // def convert(e: MessageBodyFailure) =  e match {
    //   case InvalidMessageBodyFailure(details, cause) => ()
    //   case MalformedMessageBodyFailure(details, cause) => ()
    // }
  }
}

trait LibraryService {

  import LibraryService._

  def getBooks: Observable[Book]

  def getBookById(id: Int): Task[Option[Book]]

  def searchBook(mode: BookSearchMode, value: String): Observable[Book]

  def updateBook(id: Int, updateData: BookUpdate): IO[Error, Unit]

  def deleteBook(id: Int): Task[Int]

  def insertBook(newBook: NewBook): IO[Error, Book]

  def insertAuthor(a: NewAuthor): Task[Int]

  def booksForAuthor(authorId: Int): Observable[Book]

}

class LibraryServiceImpl(
    profile: JdbcProfile,
    dbio: LibraryDbio,
    db: JdbcBackend.DatabaseDef
) extends LibraryService {
  import profile.api._

  import LibraryService._

  def getBooks = db.streamO(dbio.getBooks.transactionally)

  def getBookById(id: Int) = db.runL(dbio.getBookById(id))

  def searchBook(mode: BookSearchMode, value: String): Observable[Book] =
    mode match {
      case BookTitle =>
        db.streamO(dbio.getBooksByTitle(value))

      case AuthorName =>
        Observable
          .fromTask((for {
            author <- db.runL(dbio.getAuthorByName(value)).flatMap {
              case None =>
                IO.raiseError(
                  new EntityDoesNotExist(
                    s"Author with name=$value does not exist"
                  )
                )
              case Some(value) => IO.pure(value)
            }
            books = db
              .streamO(dbio.getBooksForAuthor(author.authorId))
              .map(Book.fromBooksRow)
          } yield books).toTask)
          .flatten
    }

  def insertAuthor(a: NewAuthor): Task[Int] = db.runL(dbio.insertAuthor(a))

  def updateBook(id: Int, updateData: BookUpdate): IO[Error, Unit] =
    for {
      action <- UIO.deferAction(implicit s =>
        UIO(for {
          mbRow <- Tables.Books.filter(_.bookId === id).result.headOption
          updatedRow <- mbRow match {
            case Some(value) =>
              println(s"Original value -> $value")
              println(s"Value to be updated with -> $updateData")
              DBIO.successful(updateData.update(value))
            case None =>
              DBIO.failed(
                EntityDoesNotExist(s"Book with id=$id does not exist")
              )
          }
          updateAction = Tables.Books.filter(_.bookId === id).update(updatedRow)
          _ = println(s"SQL = ${updateAction.statements}")
          _ <- updateAction
        } yield ())
      )
      _ <- db
        .runTryL(action.transactionally.asTry)
        .mapErrorPartial { case e: Error =>
          e
        }
    } yield ()

  def deleteBook(id: Int) = db.runL(dbio.deleteBook(id))

  def insertBook(newBook: NewBook): IO[Error, Book] =
    IO.deferAction { implicit s =>
      for {
        action <- UIO(for {
          _ <- Tables.Books
            .filter(_.isbn === newBook.isbn)
            .map(Book.fromBooksTableFn)
            .result
            .headOption
            .flatMap {
              case None => DBIO.successful(())
              case Some(_) =>
                DBIO.failed(
                  EntityAlreadyExists(
                    s"Book with isbn=${newBook.isbn} already exists"
                  )
                )
            }
          _ <- dbio.getAuthor(newBook.authorId).flatMap {
            case None =>
              DBIO.failed(
                EntityDoesNotExist(
                  s"Author with id=${newBook.authorId} does not exist"
                )
              )
            case Some(_) => DBIO.successful(())
          }
          book <- dbio.insertBookAndGetBook(newBook)
        } yield book)
        book <- db
          .runTryL(action.transactionally.asTry)
          .mapErrorPartial { case e: Error =>
            e
          }
      } yield book
    }

  def booksForAuthor(authorId: Int) =
    db.streamO(dbio.getBooksForAuthor(authorId).transactionally)
      .map(Book.fromBooksRow)

}

class LibraryDbio(val profile: JdbcProfile) {
  import profile.api._

  /*  */

  def getBooks: StreamingDBIO[Seq[Book], Book] = Query.getBooksInner.result

  def insertBookAndGetId(newBook: NewBook): DBIO[Int] =
    Query.insertBookGetId += newBook

  def insertBookAndGetBook(newBook: NewBook): DBIO[Book] =
    Query.insertBookGetBook += newBook

  def insertAuthor(newAuthor: NewAuthor): DBIO[Int] =
    Query.insertAuthorGetId += newAuthor

  def getAuthor(id: Int): DBIO[Option[Author]] =
    Query.selectAuthor(id).map(Author.fromAuthorsTableFn).result.headOption

  def getAuthorByName(name: String): DBIO[Option[Author]] =
    Tables.Authors
      .filter(_.authorName === name)
      .map(Author.fromAuthorsTableFn)
      .result
      .headOption

  def deleteBook(id: Int): DBIO[Int] = Query.selectBookById(id).delete

  def getBookById(id: Int): DBIO[Option[Book]] = Query
    .selectBookById(id)
    .map(Book.fromBooksTableFn)
    .result
    .headOption

  def getBooksByTitle(title: String): StreamingDBIO[Seq[Book], Book] =
    Tables.Books.filter(_.bookTitle === title).map(Book.fromBooksTableFn).result

  def getBooksForAuthor(
      authorId: Int
  ): StreamingDBIO[Seq[Tables.BooksRow], Tables.BooksRow] =
    Query.booksForAuthorInner(authorId).result

  private object Query {

    val getBooksInner = Book.fromBooksTable

    val insertBookGetId =
      NewBook.fromBooksTable.returning(Tables.Books.map(_.bookId))

    val insertBookGetBook = NewBook.fromBooksTable.returning(getBooksInner)

    val insertAuthorGetId =
      Tables.Authors
        .map(a => (a.authorName).mapTo[NewAuthor])
        .returning(Tables.Authors.map(_.authorId))

    // val insertAuthor = NewAuthor.fromAuthorsTable

    def booksForAuthorInner(authorId: Int) = for {
      b <- Tables.Books
      a <- Tables.Authors
      if b.authorId === a.authorId && b.authorId === authorId
    } yield b

    def selectAuthor(authorId: Int) =
      Tables.Authors.filter(_.authorId === authorId)

    def selectBookById(id: Int) = Tables.Books.filter(_.bookId === id)

    def selectBookByIsbn(isbn: String) = Tables.Books.filter(_.isbn === isbn)
  }
}

trait NoopLibraryService extends LibraryService {
  def getBooks: Observable[Book] =
    Observable.raiseError(new NotImplementedError)

  def getBookById(id: Int): Task[Option[Book]] =
    IO.terminate(new NotImplementedError)

  def searchBook(
      mode: BookSearchMode,
      value: String
  ): Observable[Book] = Observable.raiseError(new NotImplementedError)

  def updateBook(
      id: Int,
      updateData: BookUpdate
  ): IO[LibraryService.Error, Unit] = IO.terminate(new NotImplementedError)

  def deleteBook(id: Int): Task[Int] = IO.terminate(new NotImplementedError)

  def insertBook(newBook: NewBook): IO[LibraryService.Error, Book] =
    IO.terminate(new NotImplementedError)

  def insertAuthor(a: NewAuthor): Task[Int] =
    IO.terminate(new NotImplementedError)

  def booksForAuthor(authorId: Int): Observable[Book] =
    Observable.raiseError(new NotImplementedError)

}
