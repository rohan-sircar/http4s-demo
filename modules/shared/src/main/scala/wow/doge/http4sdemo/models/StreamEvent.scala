package wow.doge.http4sdemo.models

import io.circe.generic.semiauto._
import sttp.tapir.Schema
import io.scalaland.chimney.dsl._

sealed trait StreamInputEvent extends Product with Serializable { self =>
  def toOutputEvent(senderId: Int) = self match {
    case StreamInputEvent.Ack(id) => StreamEvent.Ack(id)
    case m @ StreamInputEvent.Message(_, _) =>
      m.transformInto[StreamEvent.Message]
    case m @ StreamInputEvent.Message2(_, _, _) =>
      m.into[StreamEvent.Message2]
        .withFieldConst(_.sender, senderId)
        .transform
  }
}
object StreamInputEvent {
  final case class Ack(id: Long) extends StreamInputEvent
  final case class Message(id: Long, payload: String) extends StreamInputEvent
  final case class Message2(
      id: Long,
      receiver: Int,
      payload: String
  ) extends StreamInputEvent
  // final case class Unknown(error: String) extends StreamInputEvent

  implicit val codec = deriveCodec[StreamInputEvent]

  implicit val schema = Schema.derived[StreamInputEvent]

}

sealed trait StreamEvent extends Product with Serializable
object StreamEvent {
  final case class Ack(id: Long) extends StreamEvent
  final case class Message(id: Long, payload: String) extends StreamEvent
  final case class Message2(
      id: Long,
      sender: Int,
      receiver: Int,
      payload: String
  ) extends StreamEvent
  final case class Unknown(error: String) extends StreamEvent

  implicit val codec = deriveCodec[StreamEvent]

  implicit val schema = Schema.derived[StreamEvent]
}
