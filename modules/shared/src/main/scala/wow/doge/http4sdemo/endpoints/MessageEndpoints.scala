package wow.doge.http4sdemo.endpoints

import wow.doge.http4sdemo.utils.mytapir._
import wow.doge.http4sdemo.models._
import sttp.capabilities.fs2.Fs2Streams
import monix.bio.Task
import sttp.tapir.CodecFormat

object MessageEndpoints {
  val subscribeEndpoint = baseEndpoint.get
    .in("api" / "subscribe2")
    .out(
      streamBody(Fs2Streams[Task])(
        Schema(Schema.derived[List[StreamEvent]].schemaType),
        CodecFormat.Json()
      )
    )

  val publishEndpoint = baseEndpoint.post
    .in("api" / "publish2")
    .in(
      streamBody(Fs2Streams[Task])(
        Schema(Schema.derived[List[StreamInputEvent]].schemaType),
        CodecFormat.Json()
      )
    )
    .out(emptyOutput)
}
