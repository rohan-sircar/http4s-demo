package wow.doge.http4sdemo.dto

import java.time.Instant

import io.circe.Printer
import io.circe.generic.semiauto._
import io.scalaland.chimney.dsl._
import org.http4s.EntityEncoder
import org.http4s.circe.streamJsonArrayEncoderWithPrinterOf
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.slickcodegen.Tables

final case class Book(
    id: Int,
    title: String,
    authorId: Int,
    createdAt: Instant
)
object Book {
  def tupled = (Book.apply _).tupled
  implicit val ec = deriveCodec[Book]
  // implicit def streamEntityEncoder[F[_]]
  //     : EntityEncoder[F, fs2.Stream[F, Book]] =
  //   streamJsonArrayEncoderWithPrinterOf(Printer.noSpaces)
  def fromBooksRow(row: Tables.BooksRow) = row.transformInto[Book]
  def fromBooksTableFn(implicit profile: JdbcProfile) = {
    import profile.api._
    (b: Tables.Books) => (b.id, b.title, b.authorId, b.createdAt).mapTo[Book]
  }
  def fromBooksTable(implicit profile: JdbcProfile) =
    Tables.Books.map(fromBooksTableFn)

}

final case class NewBook(title: String, authorId: Int)
object NewBook {
  def tupled = (NewBook.apply _).tupled
  implicit val decoder = deriveDecoder[NewBook]
  def fromBooksTable(implicit profile: JdbcProfile) = {
    import profile.api._

    Tables.Books.map(b => (b.title, b.authorId).mapTo[NewBook])
  }
}

final case class BookUpdate(title: Option[String], authorId: Option[Int]) {
  import com.softwaremill.quicklens._
  def update(row: Tables.BooksRow): Tables.BooksRow =
    row
      .modify(_.title)
      .setToIfDefined(title)
      .modify(_.authorId)
      .setToIfDefined(authorId)
}
object BookUpdate {
  implicit val decoder = deriveDecoder[BookUpdate]
}

final case class Author(id: Int, name: String)
object Author {
  def tupled = (Author.apply _).tupled
  implicit val codec = deriveCodec[Author]
  implicit def streamEntityEncoder[F[_]]
      : EntityEncoder[F, fs2.Stream[F, Author]] =
    streamJsonArrayEncoderWithPrinterOf(Printer.noSpaces)
}

final case class NewAuthor(name: String)

final case class BookWithAuthor(
    id: Int,
    title: String,
    author: Author,
    createdAt: Instant
)
object BookWithAuthor {
  def tupled = (BookWithAuthor.apply _).tupled
  implicit val codec = deriveCodec[BookWithAuthor]
  implicit def streamEntityEncoder[F[_]]
      : EntityEncoder[F, fs2.Stream[F, BookWithAuthor]] =
    streamJsonArrayEncoderWithPrinterOf(Printer.noSpaces)
}
