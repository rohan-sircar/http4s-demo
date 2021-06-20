package wow.doge.http4sdemo.models

import java.time.LocalDateTime

import cats.Show
import enumeratum.EnumEntry
import enumeratum._
import io.circe.Json
import io.circe.Printer
import io.circe.generic.semiauto._
import io.scalaland.chimney.dsl._
import sttp.tapir.Schema
import sttp.tapir.codec.enumeratum._
import sttp.tapir.codec.newtype._
import sttp.tapir.codec.refined._
import wow.doge.http4sdemo.models.common.Color
import wow.doge.http4sdemo.refinements.Refinements._

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
  implicit val schema = Schema.derived[Book]
}

final case class NewBook(
    bookTitle: BookTitle,
    bookIsbn: BookIsbn,
    authorId: AuthorId
)
object NewBook {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[NewBook]
  implicit val schema = Schema.derived[NewBook]
}

final case class BookUpdateRow(
    bookTitle: BookTitle,
    authorId: AuthorId
)

final case class BookUpdate(
    bookTitle: Option[BookTitle],
    authorId: Option[AuthorId]
) { self =>
  def update(row: BookUpdateRow): BookUpdateRow = row.patchUsing(self)
}
object BookUpdate {
  implicit val codec = deriveCodec[BookUpdate]
}

final case class Author(authorId: AuthorId, authorName: AuthorName)
object Author {
  def tupled = (apply _).tupled
  implicit val codec = deriveCodec[Author]
  implicit val schema = Schema.derived[Author]
}

final case class NewAuthor(name: AuthorName)
object NewAuthor {
  implicit val codec = deriveCodec[NewAuthor]
  implicit val schema = Schema.derived[NewAuthor]
}

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

}
