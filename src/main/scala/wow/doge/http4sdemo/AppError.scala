package wow.doge.http4sdemo

import io.circe.generic.semiauto._
import monix.bio.Task
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl

sealed trait AppError extends Exception {
  def message: String
  override def getMessage(): String = message
  def toResponse = {
    val dsl = Http4sDsl[Task]
    import dsl._
    implicit val codec = AppError.codec
    this match {
      case e @ AppError.EntityDoesNotExist(message) =>
        NotFound(e: AppError).hideErrors
      case e @ AppError.EntityAlreadyExists(message) =>
        BadRequest(e: AppError).hideErrors
      case e @ AppError.BadInput(message) =>
        BadRequest(e: AppError).hideErrors
    }
  }
}

object AppError {
  implicit val codec = deriveCodec[AppError]
  final case class EntityDoesNotExist(message: String) extends AppError
  final case class EntityAlreadyExists(message: String) extends AppError
  final case class BadInput(message: String) extends AppError
}
