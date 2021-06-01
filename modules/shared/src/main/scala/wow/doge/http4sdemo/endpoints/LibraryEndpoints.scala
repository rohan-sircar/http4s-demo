package wow.doge.http4sdemo.endpoints

import monix.bio.Task
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.codec.newtype._
import sttp.tapir.codec.refined._
import sttp.tapir.json.circe._
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._

object LibraryEndpoints {

  val baseEndpoint = errorEndpoint.in("api" / "books")

  val getBookById =
    baseEndpoint.get
      .in(path[BookId])
      .out(jsonBody[Book])

  val getBooks =
    baseEndpoint.get
      .in(Pagination.endpoint)
      .out(
        streamBody(Fs2Streams[Task])(
          Schema(Schema.derived[List[Book]].schemaType),
          CodecFormat.Json()
        )
      )

}
