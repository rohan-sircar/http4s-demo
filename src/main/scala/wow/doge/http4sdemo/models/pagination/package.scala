package wow.doge.http4sdemo.models

import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined._
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.TransformerF
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Refinements.PaginationRefinement
import wow.doge.http4sdemo.utils.RefinementValidation

package object pagination {

  @newtype final case class PaginationPage(inner: PaginationRefinement)
  object PaginationPage {
    def unapply(s: String): Option[PaginationPage] =
      s.toIntOption
        .flatMap(PaginationRefinement.unapply)
        .map(PaginationPage.apply)
    implicit val qpd: QueryParamDecoder[PaginationPage] =
      QueryParamDecoder[PaginationRefinement].coerce
    object matcher extends QueryParamDecoderMatcher[PaginationPage]("page")
    implicit val encoder: Encoder[PaginationPage] =
      Encoder[PaginationRefinement].coerce
    implicit val decoder: Decoder[PaginationPage] =
      Decoder[PaginationRefinement].coerce
    implicit val from =
      implicitly[
        TransformerF[RefinementValidation, Int, PaginationRefinement]
      ].coerce
    implicit val to: Transformer[PaginationPage, Int] = _.inner.value
  }

  @newtype final case class PaginationLimit(inner: PaginationRefinement)
  object PaginationLimit {
    def unapply(s: String): Option[PaginationLimit] =
      s.toIntOption
        .flatMap(PaginationRefinement.unapply)
        .map(PaginationLimit.apply)
    implicit val qpd: QueryParamDecoder[PaginationLimit] =
      QueryParamDecoder[PaginationRefinement].coerce
    object matcher extends QueryParamDecoderMatcher[PaginationLimit]("limit")
    implicit val encoder: Encoder[PaginationLimit] =
      Encoder[PaginationRefinement].coerce
    implicit val decoder: Decoder[PaginationLimit] =
      Decoder[PaginationRefinement].coerce
    implicit val from =
      implicitly[
        TransformerF[RefinementValidation, Int, PaginationRefinement]
      ].coerce
    implicit val to: Transformer[PaginationLimit, Int] = _.inner.value
  }

  //this is a derived type(from limit and page number), so constrain
  //the constructor visiblity to ensure the value within is constrained
  @newtype final case class PaginationOffset private[pagination] (toInt: Int)
  object PaginationOffset {
    implicit val encoder: Encoder[PaginationOffset] =
      Encoder[Int].coerce
    implicit val decoder: Decoder[PaginationOffset] =
      Decoder[Int].coerce
  }

}

package pagination {
  final case class Pagination(page: PaginationPage, limit: PaginationLimit) {
    val offset: PaginationOffset = PaginationOffset(
      page.inner.value * limit.inner.value
    )
  }
}
