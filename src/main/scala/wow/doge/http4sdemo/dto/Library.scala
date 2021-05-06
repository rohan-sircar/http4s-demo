package wow.doge.http4sdemo.dto

import java.time.LocalDateTime

import cats.syntax.either._
import enumeratum.EnumEntry
import enumeratum._
import io.circe.generic.semiauto._
import io.scalaland.chimney.dsl._
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.slickcodegen.Tables

final case class Book(
    bookId: Int,
    bookTitle: String,
    isbn: String,
    authorId: Int,
    createdAt: LocalDateTime
)
object Book {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[Book]
  def fromBooksRow(row: Tables.BooksRow) = row.transformInto[Book]
  def fromBooksTableFn(implicit profile: JdbcProfile) = {
    import profile.api._
    (b: Tables.Books) =>
      (b.bookId, b.bookTitle, b.isbn, b.authorId, b.createdAt).mapTo[Book]
  }
  def fromBooksTable(implicit profile: JdbcProfile) =
    Tables.Books.map(fromBooksTableFn)

}

final case class NewBook(bookTitle: String, isbn: String, authorId: Int)
object NewBook {
  def tupled = (apply _).tupled
  implicit val decoder = deriveDecoder[NewBook]
  def fromBooksTable(implicit profile: JdbcProfile) = {
    import profile.api._

    Tables.Books.map(b => (b.bookTitle, b.isbn, b.authorId).mapTo[NewBook])
  }
}

final case class BookUpdate(title: Option[String], authorId: Option[Int]) {
  import com.softwaremill.quicklens._
  def update(row: Tables.BooksRow): Tables.BooksRow =
    row
      .modify(_.bookTitle)
      .setToIfDefined(title)
      .modify(_.authorId)
      .setToIfDefined(authorId)
}
object BookUpdate {
  implicit val codec = deriveCodec[BookUpdate]
}

final case class Author(authorId: Int, authorName: String)
object Author {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[Author]
  def fromAuthorsRow(row: Tables.AuthorsRow) = row.transformInto[Author]
  def fromAuthorsTableFn(implicit profile: JdbcProfile) = {
    import profile.api._
    (a: Tables.Authors) => (a.authorId, a.authorName).mapTo[Author]
  }
}

final case class NewAuthor(name: String)
object NewAuthor {
  // def fromAuthorsTable(implicit profile: JdbcProfile) = {
  //   import profile.api._

  //   Tables.Authors.map(a => (a.authorName).mapTo[NewAuthor])
  // }
}

final case class BookWithAuthor(
    id: Int,
    title: String,
    isbn: String,
    author: Author,
    createdAt: LocalDateTime
)
object BookWithAuthor {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[BookWithAuthor]
}

sealed trait BookSearchMode extends EnumEntry
object BookSearchMode extends Enum[BookSearchMode] {
  val values = findValues
  case object BookTitle extends BookSearchMode
  case object AuthorName extends BookSearchMode

  implicit val yearQueryParamDecoder: QueryParamDecoder[BookSearchMode] =
    QueryParamDecoder[String].emap(s =>
      withNameEither(s).leftMap(e => ParseFailure(e.getMessage, e.getMessage))
    )
  object Matcher extends QueryParamDecoderMatcher[BookSearchMode]("mode")

}
