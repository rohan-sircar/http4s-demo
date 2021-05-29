package wow.doge.http4sdemo.server.implicits

import cats.syntax.either._
import io.estatico.newtype.ops._
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.models.common._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements._

trait QueryParamDecoders {
  implicit val qpdPaginationPage: QueryParamDecoder[PaginationPage] =
    QueryParamDecoder[PaginationRefinement]
      .coerce[QueryParamDecoder[PaginationPage]]
  object PaginationPageMatcher
      extends QueryParamDecoderMatcher[PaginationPage]("page")

  implicit val qpdPaginationLimit: QueryParamDecoder[PaginationLimit] =
    QueryParamDecoder[PaginationRefinement]
      .coerce[QueryParamDecoder[PaginationLimit]]
  object PaginationLimitMatcher
      extends QueryParamDecoderMatcher[PaginationLimit]("limit")

  implicit val qpdBookSearchMode: QueryParamDecoder[BookSearchMode] =
    QueryParamDecoder[String].emap(s =>
      BookSearchMode
        .withNameEither(s)
        .leftMap(e => ParseFailure(e.getMessage, e.getMessage))
    )
  object BookSearchModeMatcher
      extends QueryParamDecoderMatcher[BookSearchMode]("mode")

  implicit val qpdColor: QueryParamDecoder[Color] =
    QueryParamDecoder[String].emap(s =>
      Color
        .withNameEither(s)
        .leftMap(e => ParseFailure(e.getMessage, e.getMessage))
    )
  object ColorMatcher extends QueryParamDecoderMatcher[Color]("color")
}
object QueryParamDecoders extends QueryParamDecoders
