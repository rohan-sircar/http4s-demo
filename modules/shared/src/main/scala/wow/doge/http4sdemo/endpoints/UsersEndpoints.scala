package wow.doge.http4sdemo.endpoints

import monix.bio.Task
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.CodecFormat
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.refinements._
import wow.doge.http4sdemo.utils.mytapir._

object UsersEndpoints {
  val baseUserEndpoint = basePublicEndpoint.in("users")

  val getUserByUsernameEndpoint =
    baseUserEndpoint.get.in(path[Username]).out(jsonBody[User])

  val searchUsersEndpoint =
    baseUserEndpoint.get
      .in("search")
      .in(Pagination.endpoint)
      .in(query[SearchQuery]("q"))
      .out(
        streamBody(Fs2Streams[Task])(
          Schema(Schema.derived[List[User]].schemaType),
          CodecFormat.Json()
        )
      )
}
