package wow.doge.http4sdemo.models

import enumeratum.EnumEntry
import enumeratum._

package object common {}

package common {
  sealed trait Color extends EnumEntry with EnumEntry.UpperSnakecase
  object Color extends Enum[Color] with CirceEnum[Color] {
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

  }

}
