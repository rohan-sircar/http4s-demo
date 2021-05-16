package wow.doge.http4sdemo.models

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.collection._
import eu.timepit.refined.numeric._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined._
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.TransformerF
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.utils.RefinementValidation

object Refinements {

  type IdRefinement = Int Refined Positive
  object IdRefinement extends RefinedTypeOps[IdRefinement, Int] {
    //for use in http router dsl, which takes a string as input
    def unapply(s: String): Option[IdRefinement] =
      s.toIntOption.flatMap(unapply)
  }

  type StringRefinement = String Refined Size[Interval.Closed[5, 50]]
  object StringRefinement extends RefinedTypeOps[StringRefinement, String]

  type PaginationRefinement = Int Refined Interval.Closed[0, 50]
  object PaginationRefinement extends RefinedTypeOps[PaginationRefinement, Int]

  //in case your're thinking "jeez this is boilerplatey", I'll have you know
  //I have a vscode snippet that takes care of most of this

  @newtype final case class BookId(id: IdRefinement)
  object BookId {
    def unapply(s: String): Option[BookId] =
      s.toIntOption.flatMap(IdRefinement.unapply).map(BookId.apply)
    implicit val encoder: Encoder[BookId] =
      implicitly[Encoder[IdRefinement]].coerce
    implicit val decoder: Decoder[BookId] =
      implicitly[Decoder[IdRefinement]].coerce
    implicit val fromT =
      implicitly[TransformerF[RefinementValidation, Int, IdRefinement]].coerce
    implicit val toT: Transformer[BookId, Int] = _.id.value
  }

  @newtype final case class AuthorId(id: IdRefinement)
  object AuthorId {
    def from(num: Int): Either[String, AuthorId] =
      IdRefinement.from(num).map(AuthorId.apply)
    def unapply(s: String): Option[AuthorId] =
      s.toIntOption.flatMap(IdRefinement.unapply).map(AuthorId.apply)
    implicit val encoder: Encoder[AuthorId] =
      implicitly[Encoder[IdRefinement]].coerce
    implicit val decoder: Decoder[AuthorId] =
      implicitly[Decoder[IdRefinement]].coerce
    implicit val fromT: TransformerF[RefinementValidation, Int, AuthorId] =
      implicitly[TransformerF[RefinementValidation, Int, IdRefinement]].coerce
    implicit val toT: Transformer[AuthorId, Int] = _.id.value
  }

  @newtype final case class BookTitle(title: StringRefinement)
  object BookTitle {
    implicit val encoder: Encoder[BookTitle] =
      implicitly[Encoder[StringRefinement]].coerce
    implicit val decoder: Decoder[BookTitle] =
      implicitly[Decoder[StringRefinement]].coerce
    implicit val fromT =
      implicitly[
        TransformerF[RefinementValidation, String, StringRefinement]
      ].coerce
    implicit val toT: Transformer[BookTitle, String] = _.title.value
  }

  @newtype final case class BookIsbn(inner: StringRefinement)
  object BookIsbn {
    implicit val encoder: Encoder[BookIsbn] =
      implicitly[Encoder[StringRefinement]].coerce
    implicit val decoder: Decoder[BookIsbn] =
      implicitly[Decoder[StringRefinement]].coerce
    implicit val fromT =
      implicitly[
        TransformerF[RefinementValidation, String, StringRefinement]
      ].coerce
    implicit val toT: Transformer[BookIsbn, String] = _.inner.value
  }

  @newtype final case class AuthorName(name: StringRefinement)
  object AuthorName {
    implicit val encoder: Encoder[AuthorName] =
      implicitly[Encoder[StringRefinement]].coerce
    implicit val decoder: Decoder[AuthorName] =
      implicitly[Decoder[StringRefinement]].coerce
    implicit val fromT =
      implicitly[
        TransformerF[RefinementValidation, String, StringRefinement]
      ].coerce
    implicit val toT: Transformer[AuthorName, String] = _.name.value
  }

}
