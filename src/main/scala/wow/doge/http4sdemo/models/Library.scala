package wow.doge.http4sdemo.models

import java.time.LocalDateTime

import cats.syntax.either._
import enumeratum.EnumEntry
import enumeratum._
import io.circe.generic.semiauto._
import io.scalaland.chimney.cats._
import io.scalaland.chimney.dsl._
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.models.Refinements._
import wow.doge.http4sdemo.slickcodegen.Tables
import wow.doge.http4sdemo.utils.RefinementValidation

final case class Book(
    bookId: BookId,
    bookTitle: BookTitle,
    isbn: BookIsbn,
    authorId: AuthorId,
    createdAt: LocalDateTime
)
object Book {
  implicit val codec = deriveCodec[Book]
}

final case class RawNewBook(bookTitle: String, isbn: String, authorId: Int)
object RawNewBook {
  def tupled = (apply _).tupled
  implicit val decoder = deriveDecoder[RawNewBook]
  def fromBooksTable(implicit profile: JdbcProfile) = {
    import profile.api._

    Tables.Books.map(b => (b.bookTitle, b.isbn, b.authorId).mapTo[RawNewBook])
  }
}

final case class NewBook(
    bookTitle: BookTitle,
    isbn: BookIsbn,
    authorId: AuthorId
)
object NewBook {
  implicit val codec = deriveCodec[NewBook]
}

final case class BookUpdate(
    bookTitle: Option[BookTitle],
    authorId: Option[AuthorId]
) {
  def update(row: Tables.BooksRow): Tables.BooksRow =
    row.patchUsing(this)
}
object BookUpdate {
  implicit val codec = deriveCodec[BookUpdate]
}

final case class Author(authorId: AuthorId, authorName: AuthorName)
object Author {
  implicit val codec = deriveCodec[Author]
  def fromAuthorsRow(row: Tables.AuthorsRow) =
    row.transformIntoF[RefinementValidation, Author]
}

final case class RawNewAuthor(name: String)
object RawNewAuthor {
  // def fromAuthorsTable(implicit profile: JdbcProfile) = {
  //   import profile.api._

  //   Tables.Authors.map(a => (a.authorName).mapTo[NewAuthor])
  // }
}

final case class NewAuthor(name: AuthorName)

final case class BookWithAuthor(
    id: BookId,
    title: BookTitle,
    isbn: BookIsbn,
    author: Author,
    createdAt: LocalDateTime
)
object BookWithAuthor {
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
