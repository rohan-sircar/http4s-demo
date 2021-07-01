package wow.doge.http4sdemo.refinements

import cats.kernel.Eq
import eu.timepit.refined.cats._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined._
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.TransformerF
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.refinements.RefinementValidation

object Refinements {

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
    // implicit val tt = implicitly[slick.ast.TypedType[IdRefinement]].coerce
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

  @newtype final case class NumRows(toInt: Int)
  object NumRows {
    implicit val encoder: Encoder[NumRows] = Encoder[Int].coerce
    implicit val decoder: Decoder[NumRows] = Decoder[Int].coerce

    implicit class NumRowsOps(private val N: NumRows) extends AnyVal {
      def :+(T: NumRows) = NumRows(N.toInt + T.toInt)
    }
    // implicit val from =
    //   implicitly[TransformerF[RefinementValidation, Int, Int]].coerce
    // implicit val to: Transformer[NumRows, Int] = _.inner.value
  }

  @newtype final case class UserId(inner: IdRefinement)
  object UserId {
    implicit val encoder: Encoder[UserId] =
      Encoder[IdRefinement].coerce
    implicit val decoder: Decoder[UserId] =
      Decoder[IdRefinement].coerce
    implicit val fromT =
      implicitly[TransformerF[RefinementValidation, Int, IdRefinement]].coerce
    implicit val toT: Transformer[UserId, Int] = _.inner.value

    implicit val eq: Eq[UserId] = Eq[IdRefinement].coerce

    implicit class UserIdOps(id: UserId) {

      /** @throws java.lang.IllegalArgumentException
        */
      @SuppressWarnings(Array("org.wartremover.warts.Throw"))
      def :+(that: UserId) = UserId(
        IdRefinement
          .from(id.inner.value + that.inner.value)
          .getOrElse(throw new IllegalArgumentException("Invalid id"))
      )
    }
  }

  @newtype final case class Username(inner: UsernameRefinement)
  object Username {
    implicit val encoder: Encoder[Username] =
      Encoder[UsernameRefinement].coerce
    implicit val decoder: Decoder[Username] =
      Decoder[UsernameRefinement].coerce
    implicit val fromT =
      implicitly[
        TransformerF[RefinementValidation, String, UsernameRefinement]
      ].coerce
    implicit val toT: Transformer[Username, String] = _.inner.value

    implicit val eq: Eq[Username] = Eq[UsernameRefinement].coerce
  }

  @newtype final case class UnhashedUserPassword(
      inner: UnhashedPasswordRefinement
  )
  object UnhashedUserPassword {
    implicit val encoder: Encoder[UnhashedUserPassword] =
      Encoder[UnhashedPasswordRefinement].coerce
    implicit val decoder: Decoder[UnhashedUserPassword] =
      Decoder[UnhashedPasswordRefinement].coerce
    implicit val fromT =
      implicitly[
        TransformerF[RefinementValidation, String, UnhashedPasswordRefinement]
      ].coerce
    implicit val toT: Transformer[UnhashedUserPassword, String] = _.inner.value
  }

  @newtype final case class HashedUserPassword(inner: HashedPasswordRefinement)
  object HashedUserPassword {
    implicit val encoder: Encoder[HashedUserPassword] =
      Encoder[HashedPasswordRefinement].coerce
    implicit val decoder: Decoder[HashedUserPassword] =
      Decoder[HashedPasswordRefinement].coerce
    implicit val fromT =
      implicitly[
        TransformerF[RefinementValidation, String, HashedPasswordRefinement]
      ].coerce
    implicit val toT: Transformer[HashedUserPassword, String] = _.inner.value
  }

  @newtype final case class UserEmail(inner: EmailRefinement)
  object UserEmail {
    implicit val encoder: Encoder[UserEmail] =
      Encoder[EmailRefinement].coerce
    implicit val decoder: Decoder[UserEmail] =
      Decoder[EmailRefinement].coerce
    implicit val fromT =
      implicitly[
        TransformerF[RefinementValidation, String, EmailRefinement]
      ].coerce
    implicit val toT: Transformer[UserEmail, String] = _.inner.value
  }

}
