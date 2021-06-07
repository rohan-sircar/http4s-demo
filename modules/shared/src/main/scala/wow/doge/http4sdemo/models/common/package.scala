package wow.doge.http4sdemo.models

import enumeratum.EnumEntry
import enumeratum._
import enumeratum.values.CatsValueEnum
import enumeratum.values.IntCirceEnum
import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry

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

  //make the constructor private to prevent creating
//arbitrary values
  sealed abstract class UserRole private (val value: Int)
      extends IntEnumEntry
      with EnumEntry.UpperSnakecase
  object UserRole
      extends IntEnum[UserRole]
      with CatsValueEnum[Int, UserRole]
      with IntCirceEnum[UserRole] {
    val values = findValues
    case object SuperUser extends UserRole(0)
    case object Admin extends UserRole(1)
    case object User extends UserRole(2)
  }

}
