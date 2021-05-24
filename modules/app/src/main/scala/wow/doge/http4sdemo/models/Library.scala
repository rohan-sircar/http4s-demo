package wow.doge.http4sdemo.models

import java.time.LocalDateTime

import cats.Show
import cats.syntax.either._
import enumeratum.EnumEntry
import enumeratum._
import io.circe.Json
import io.circe.Printer
import io.circe.generic.semiauto._
import io.scalaland.chimney.dsl._
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import wow.doge.http4sdemo.models.Refinements._
import wow.doge.http4sdemo.models.common.Color
import wow.doge.http4sdemo.profile.{ExtendedPgProfile => JdbcProfile}
import wow.doge.http4sdemo.slickcodegen.Tables

final case class Book(
    bookId: BookId,
    bookTitle: BookTitle,
    bookIsbn: BookIsbn,
    authorId: AuthorId,
    createdAt: LocalDateTime
)
object Book {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[Book]

  def fromBooksTableFn(implicit profile: JdbcProfile) = {
    import profile.api._
    (b: Tables.Books) =>
      (b.bookId, b.bookTitle, b.bookIsbn, b.authorId, b.createdAt).mapTo[Book]
  }
}

final case class NewBook(
    bookTitle: BookTitle,
    bookIsbn: BookIsbn,
    authorId: AuthorId
)
object NewBook {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[NewBook]

  def fromBooksTableFn(implicit profile: JdbcProfile) = {
    import profile.api._
    (b: Tables.Books) => (b.bookTitle, b.bookIsbn, b.authorId).mapTo[NewBook]
  }
}

final case class BookUpdateRow(
    bookTitle: BookTitle,
    authorId: AuthorId
)

final case class BookUpdate(
    bookTitle: Option[BookTitle],
    authorId: Option[AuthorId]
) {
  def update(row: BookUpdateRow): BookUpdateRow = row.patchUsing(this)
}
object BookUpdate {
  implicit val codec = deriveCodec[BookUpdate]
}

final case class Author(authorId: AuthorId, authorName: AuthorName)
object Author {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[Author]
  def fromAuthorsTableFn(implicit profile: JdbcProfile) = {
    import profile.api._
    (a: Tables.Authors) => (a.authorId, a.authorName).mapTo[Author]
  }
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

sealed trait BookSearchMode extends EnumEntry with EnumEntry.Hyphencase
object BookSearchMode extends Enum[BookSearchMode] {
  val values = findValues
  case object BookTitle extends BookSearchMode
  case object AuthorName extends BookSearchMode

  implicit val qpd: QueryParamDecoder[BookSearchMode] =
    QueryParamDecoder[String].emap(s =>
      withNameEither(s).leftMap(e => ParseFailure(e.getMessage, e.getMessage))
    )
  object Matcher extends QueryParamDecoderMatcher[BookSearchMode]("mode")

}

final case class Extra(
    extrasId: Int,
    color: Color,
    metadata: Json,
    content: String
)
object Extra {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[Extra]

  def fromExtrasTableFn(implicit profile: JdbcProfile) = {
    import profile.api._
    (e: Tables.Extras) =>
      (e.extrasId, e.color, e.metadataJson, e.content).mapTo[Extra]
  }
}

final case class NewExtra(
    color: Color,
    metadata: Json,
    content: String
)
object NewExtra {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[NewExtra]

  implicit val show = Show.show[NewExtra] { ne =>
    val printer = Printer.noSpaces
    val color = ne.color.entryName
    val m = printer.print(ne.metadata)
    val content = ne.content
    s"NewExtra($color,$m,$content)"
  }

  def fromExtrasTableFn(implicit profile: JdbcProfile) = {
    import profile.api._
    (e: Tables.Extras) => (e.color, e.metadataJson, e.content).mapTo[NewExtra]
  }
}
