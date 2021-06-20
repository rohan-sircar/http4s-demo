package wow.doge.http4sdemo.models

import io.circe.Decoder
import io.circe.generic.semiauto._
import sttp.tapir.Schema
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.utils.mytapir._

final case class NewUser(
    username: Username,
    password: HashedUserPassword,
    role: UserRole
)
object NewUser {
  val tupled = (this.apply _).tupled
  implicit val decoder: Decoder[NewUser] = deriveDecoder
  implicit val schema = Schema.derived[NewUser]
}

final case class UserEntity(
    id: UserId,
    username: Username,
    password: HashedUserPassword,
    role: UserRole
)
object UserEntity {
  val tupled = (this.apply _).tupled
}

final case class User(
    id: UserId,
    username: Username,
    role: UserRole
)
object User {
  val tupled = (this.apply _).tupled
  implicit val codec: io.circe.Codec[User] = deriveCodec

  implicit val schema = Schema.derived[User]

}

final case class UserIdentity(
    id: UserId,
    username: Username,
    role: UserRole
)
object UserIdentity {
  val tupled = (this.apply _).tupled
  implicit val codec: io.circe.Codec[UserIdentity] = deriveCodec

  implicit val schema = Schema.derived[UserIdentity]

  val Claim = "userDetails"
}

final case class UserLogin(
    username: Username,
    password: UnhashedUserPassword
)
object UserLogin {
  val tupled = (this.apply _).tupled
  implicit val codec: io.circe.Codec[UserLogin] = deriveCodec
  implicit val schema = Schema.derived[UserLogin]
}

final case class UserRegistration(
    username: Username,
    password: UnhashedUserPassword
)
object UserRegistration {
  implicit val codec: io.circe.Codec[UserRegistration] = deriveCodec
  implicit val schema = Schema.derived[UserRegistration]
}
