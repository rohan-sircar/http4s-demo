package wow.doge.http4sdemo.models

import io.circe.Codec
import io.circe.generic.semiauto._

final case class NewUser(email: String)

final case class User(id: String, email: String)
object User {
  val tupled = (this.apply _).tupled
  implicit val codec: Codec[User] = deriveCodec
}
