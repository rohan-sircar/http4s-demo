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
  final case class MailClientError(error: MailClientErrorCases)
      extends AppError {
    val message = error.message
  }

  sealed trait MailClientErrorCases {
    def message: String
  }

  final case class CouldNotConnectError(message: String)
      extends MailClientErrorCases
  final case class SendFailedError(message: String) extends MailClientErrorCases
  object MailClientErrorCases {
    implicit val codec = deriveCodec[MailClientErrorCases]
    implicit val schema = Schema.derived[MailClientErrorCases]
  }

  implicit val schema = Schema.derived[AppError]
}
