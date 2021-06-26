package wow.doge.http4sdemo.endpoints

import monix.bio.Task
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.CodecFormat
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.utils.mytapir._

object MessageEndpoints {
  val subscribeEndpoint = baseEndpoint.get
    .in("subscribe2")
    .out(
      streamBody(Fs2Streams[Task])(
        Schema(Schema.derived[List[StreamEvent]].schemaType),
        CodecFormat.Json()
      )
    )

  val publishEndpoint = baseEndpoint.post
    .in("publish2")
    .in(
      streamBody(Fs2Streams[Task])(
        Schema(Schema.derived[List[StreamInputEvent]].schemaType),
        CodecFormat.Json()
      )
    )
    .out(emptyOutput)
}
