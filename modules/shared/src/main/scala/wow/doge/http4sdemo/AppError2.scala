package wow.doge.http4sdemo

import io.circe.generic.semiauto._
import sttp.tapir.Schema

sealed trait AppError2 extends Exception {
  def message: String
  override def getMessage(): String = message
}

object AppError2 {
  implicit val codec = deriveCodec[AppError2]
  final case class EntityDoesNotExist(message: String) extends AppError2
  final case class EntityAlreadyExists(message: String) extends AppError2
  final case class BadInput(message: String) extends AppError2
  final case class AuthError(message: String) extends AppError2

  implicit val schema = Schema.derived[AppError2]
}
