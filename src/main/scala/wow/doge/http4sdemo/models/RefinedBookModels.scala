package wow.doge.http4sdemo.models

import java.time.LocalDateTime

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.numeric._
import eu.timepit.refined.types.string.NonEmptyFiniteString
import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined._
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.TransformerF
import io.scalaland.chimney.cats._
import io.scalaland.chimney.dsl._
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.slickcodegen.Tables

object Refinements {
  type IdRefinement = Int Refined Positive
  object IdRefinement extends RefinedTypeOps[IdRefinement, Int]
  @newtype final case class BookId(id: IdRefinement)
  object BookId {
    implicit val encoder =
      implicitly[Encoder[IdRefinement]].coerce
    implicit val decoder: Decoder[BookId] =
      implicitly[Decoder[IdRefinement]].coerce
    implicit val to =
      implicitly[TransformerF[RefinementValidation, Int, IdRefinement]].coerce
    // src =>
    //   IdRefinement.from(src).map(BookId.apply).toValidatedNec
    implicit val from: Transformer[BookId, Int] = _.id.value
  }
//   type BookTitle = NonEmptyString And

//   implicit val nesTransformerOption =
//     new TransformerF[Option, String, NonEmptyString] {
//       def transform(src: String): Option[NonEmptyString] =
//         NonEmptyString.from(src).toOption
//     }

//   implicit val nesTransformerEither2
//       : TransformerF[Either[String, +*], String, NonEmptyString] =
//     src => NonEmptyString.from(src)

//   implicit val blahTransformerVnec: TransformerF[V, String, MyType] =
//     src =>
//       NonEmptyString
//         .from(src)
//         .toValidatedNec

//   implicit val nesTransformerVnec
//       : TransformerF[RefinementValidation, String, NonEmptyString] =
//     src =>
//       NonEmptyString
//         .from(src)
//         .toValidatedNec

//   implicit def stringToNefsTransformerVnec[N <: Int](implicit
//       rt: RefinedType.AuxT[NonEmptyFiniteString[N], String],
//       wn: Witness.Aux[N]
//   ): TransformerF[RefinementValidation, String, NonEmptyFiniteString[N]] =
//     src =>
//       NonEmptyFiniteString[N]
//         .from(src)
//         .toValidatedNec
//   implicit def nefsToStringTransformerVnec[N <: Int]
//       : io.scalaland.chimney.Transformer[NonEmptyFiniteString[N], String] =
//     src => src.value

//   implicit val DomainIdTransformer =
//     new TransformerF[Option, Int, DomainId] {
//       def transform(src: Int): Option[DomainId] =
//         DomainId.from(src).toOption
//     }
//   implicit val IntToDomainIdTransformer
//       : TransformerF[RefinementValidation, Int, IdRefinement] =
//     src => IdRefinement.from(src).toValidatedNec
//   implicit val DomainIdToIntTransformer
//       : io.scalaland.chimney.Transformer[IdRefinement, Int] = _.value

}

import Refinements._

final case class Book2(
    bookId: BookId,
    bookTitle: NonEmptyFiniteString[5],
    isbn: NonEmptyFiniteString[5],
    authorId: Int,
    createdAt: LocalDateTime
)
object Book2 {
  def tupled = (apply _).tupled
//   implicit val codec = deriveCodec[Book2]

//   implicit val fromRowTransformer = TransformerF
//     .define[RefinementValidation, Tables.BooksRow, Book2]
//     .withFieldComputedF(
//       _.bookId,
//       br => IdRefinement.from(br.bookId).map(BookId.apply).toValidatedNec
//     )
//     .buildTransformer

//   implicit val toRowTransformer = io.scalaland.chimney.Transformer
//     .define[Book2, Tables.BooksRow]
//     .withFieldComputed(_.bookId, _.bookId.id.value)
//     .buildTransformer

//   implicit val transformerBook = TransformerF
//     .define[RefinementValidation, Book, Book2]
//     .withFieldComputedF(
//       _.bookId,
//       br => IdRefinement.from(br.bookId).map(BookId.apply).toValidatedNec
//     )
//     .buildTransformer

  def fromBooksRow(row: Tables.BooksRow) =
    row.transformIntoF[RefinementValidation, Book2]
  def toBooksRow(book: Book2) = book.transformInto[Tables.BooksRow]
//   def fromRawBook(book: Book) = book.transformIntoF[RefinementValidation, Book2]
//   def fromBooksTableFn(implicit profile: JdbcProfile) = {
//     import profile.api._
//     (b: Tables.Books) =>
//       (b.bookId, b.bookTitle, b.isbn, b.authorId, b.createdAt).mapTo[Book2]
//   }
//   def fromBooksTable(implicit profile: JdbcProfile) =
//     Tables.Books.map(fromBooksTableFn)

}

// final case class NewBook2(bookTitle: String, isbn: String, authorId: Int)
// object NewBook2 {
//   def tupled = (apply _).tupled
//   implicit val decoder = deriveDecoder[NewBook2]
//   def fromBooksTable(implicit profile: JdbcProfile) = {
//     import profile.api._

//     Tables.Books.map(b => (b.bookTitle, b.isbn, b.authorId).mapTo[NewBook])
//   }
// }

// final case class BookUpdate2(title: Option[String], authorId: Option[Int]) {
//   import com.softwaremill.quicklens._
//   def update(row: Tables.BooksRow): Tables.BooksRow =
//     row
//       .modify(_.bookTitle)
//       .setToIfDefined(title)
//       .modify(_.authorId)
//       .setToIfDefined(authorId)
// }
// object BookUpdate2 {
//   implicit val codec = deriveCodec[BookUpdate2]
// }
