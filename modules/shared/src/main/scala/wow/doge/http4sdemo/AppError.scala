package wow.doge.http4sdemo

import io.circe.generic.semiauto._
import sttp.tapir.Schema

sealed trait AppError extends Exception {
  def message: String
  override def getMessage(): String = message
}

object AppError {
  implicit val codec = deriveCodec[AppError]
  final case class EntityDoesNotExist(message: String) extends AppError
  final case class EntityAlreadyExists(message: String) extends AppError
  final case class BadInput(message: String) extends AppError
  final case class AuthError(message: String) extends AppError

  implicit val schema = Schema.derived[AppError]
}
