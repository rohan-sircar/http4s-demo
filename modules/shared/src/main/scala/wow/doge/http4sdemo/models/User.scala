package wow.doge.http4sdemo.models

import enumeratum.EnumEntry
import enumeratum.values.CatsValueEnum
import enumeratum.values.IntCirceEnum
import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import sttp.tapir.Schema
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.utils.mytapir._

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
  case object Admin extends UserRole(0)
  case object User extends UserRole(1)
}

final case class NewUser(
    username: Username,
    password: UserPassword,
    role: UserRole
)
object NewUser {
  // val tupled = (this.apply _).tupled
  implicit val decoder: Decoder[NewUser] = deriveDecoder
  implicit val schema = Schema.derived[NewUser]
}

final case class User(id: UserId, username: Username, role: UserRole)
object User {
  val tupled = (this.apply _).tupled
  implicit val codec: Codec[User] = deriveCodec
  implicit val schema = Schema.derived[User]

  val Claim = "userDetails"
}

final case class UserLogin(
    username: Username,
    password: UserPassword
)
object UserLogin {
  implicit val Decoder: Decoder[UserLogin] = deriveDecoder
  //redact out password field
  implicit val encoder: Encoder[UserLogin] =
    Encoder.forProduct2("username", "password")(u => (u.username, "[REDACTED]"))

  implicit val schema = Schema.derived[UserLogin]
}
