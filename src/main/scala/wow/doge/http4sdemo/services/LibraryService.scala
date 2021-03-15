package wow.doge.http4sdemo.services

import monix.bio.IO
import monix.bio.Task
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.dto.Book
import wow.doge.http4sdemo.dto.BookUpdate
import wow.doge.http4sdemo.dto.NewBook
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.slickcodegen.Tables

class LibraryService(
    profile: JdbcProfile,
    dbio: LibraryDbio,
    db: JdbcBackend.DatabaseDef
) {
  import profile.api._

  def getBooks = db.streamO(dbio.getBooks)

  def getBookById(id: Int) = db.runL(dbio.getBook(id))

  // .map(b =>
  //   (b.title, b.authorId, b.createdAt).mapTo[BookUpdateEntity]
  // )

  def updateBook(id: Int, updateData: BookUpdate) =
    for {
      action <- IO.deferAction { implicit s =>
        Task(for {
          mbRow <- dbio.selectBook(id).result.headOption
          updatedRow <- mbRow match {
            case Some(value) =>
              println(s"Original value -> $value")
              println(s"Value to be updated with -> $updateData")
              DBIO.successful(updateData.update(value))
            case None =>
              DBIO.failed(new Exception(s"Book with id $id does not exist"))
          }
          updateAction = dbio.selectBook(id).update(updatedRow)
          _ = println(s"SQL = ${updateAction.statements}")
          _ <- updateAction
        } yield ())
      }
      _ <- db.runL(action.transactionally.asTry).flatMap(Task.fromTry)
    } yield ()

  def deleteBook(id: Int) = db.runL(dbio.deleteBook(id))

  def insertBook(newBook: NewBook) =
    Task.deferFutureAction { implicit s =>
      val action = for {
        id <- dbio.insertBookAndGetId(newBook)
        book <- dbio.getBook(id)
      } yield book.get
      db.run(action.transactionally)
    }

  def booksForAuthor(authorId: Int) =
    db.streamO(dbio.booksForAuthor(authorId)).map(Book.fromBooksRow)

}

class LibraryDbio(val profile: JdbcProfile) {
  import profile.api._

  def getBooks: StreamingDBIO[Seq[Book], Book] = Query.getBooksInner.result

  def insertBookAndGetId(newBook: NewBook): DBIO[Int] =
    Query.insertBookGetId += newBook

  def insertBookAndGetBook(newBook: NewBook): DBIO[Book] =
    Query.insertBookGetBook += newBook

  def selectBook(id: Int) = Tables.Books.filter(_.id === id)

  def deleteBook(id: Int) = selectBook(id).delete

  def getBook(id: Int) = selectBook(id)
    .map(Book.fromBooksTableFn)
    .result
    .headOption

  def booksForAuthor(authorId: Int) = Query.booksForAuthorInner(authorId).result

  private object Query {

    val getBooksInner = Book.fromBooksTable

    val insertBookGetId =
      NewBook.fromBooksTable.returning(Tables.Books.map(_.id))

    val insertBookGetBook = NewBook.fromBooksTable.returning(getBooksInner)

    def booksForAuthorInner(authorId: Int) = for {
      b <- Tables.Books
      a <- selectAuthor(authorId) if b.authorId === a.id
    } yield b

    def selectAuthor(authorId: Int) = Tables.Authors.filter(_.id === authorId)
  }
}
