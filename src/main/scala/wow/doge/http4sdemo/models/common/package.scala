package wow.doge.http4sdemo.models

import cats.syntax.either._
import enumeratum.EnumEntry
import enumeratum._
import io.circe.generic.semiauto._
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import wow.doge.http4sdemo.profile.{ExtendedPgProfile => JdbcProfile}

package object common {}

package common {
  sealed trait Color extends EnumEntry with EnumEntry.UpperSnakecase
  object Color extends Enum[Color] {
    val values = findValues
    //format: off
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color

    //for use with slick DSL
    val red: Color   = Red
    val green: Color = Green
    val blue: Color  = Blue
    //format: on

    implicit val codec = deriveCodec[Color]

    implicit val col = JdbcProfile.mappedColumnTypeForEnum(Color)

    implicit val qpd: QueryParamDecoder[Color] =
      QueryParamDecoder[String].emap(s =>
        withNameEither(s).leftMap(e => ParseFailure(e.getMessage, e.getMessage))
      )
    object Matcher extends QueryParamDecoderMatcher[Color]("color")

  }

}
