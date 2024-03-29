package wow.doge.http4sdemo.models

import enumeratum.EnumEntry
import enumeratum._
import enumeratum.values.IntCirceEnum
import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry

package object common {}

package common {

  import enumeratum.values.CatsOrderValueEnum
  sealed trait Color extends EnumEntry with EnumEntry.UpperSnakecase
  object Color extends Enum[Color] with CirceEnum[Color] {
    val values = findValues
    //format: off
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color

    //for use with slick DSL
    val red  : Color = Red
    val green: Color = Green
    val blue : Color = Blue
    //format: on

  }

  //make the constructor private to prevent creating
  //arbitrary values
  sealed abstract class UserRole private (val value: Int)
      extends IntEnumEntry
      with EnumEntry.UpperSnakecase
  object UserRole
      extends CatsOrderValueEnum[Int, UserRole]
      with IntEnum[UserRole]
      with IntCirceEnum[UserRole] {
    val values = findValues
    //format: off
    case object SuperUser extends UserRole(0)
    case object Admin     extends UserRole(1)
    case object User      extends UserRole(2)
    
    val superuser: UserRole = SuperUser
    val admin    : UserRole = Admin    
    val user     : UserRole = User     
    //format: on
  }

}
