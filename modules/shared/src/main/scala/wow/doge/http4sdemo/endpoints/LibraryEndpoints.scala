package wow.doge.http4sdemo.endpoints

import monix.bio.Task
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.codec.newtype._
import sttp.tapir.codec.refined._
import sttp.tapir.json.circe._
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._

object LibraryEndpoints {

  val baseBookEndpoint = baseEndpoint.in("api" / "books")

  val getBookById =
    baseBookEndpoint.get
      .in(path[BookId])
      .out(jsonBody[Book])

  val authedGetBookById = basePrivateEndpoint.get
    .in("books")
    .in(path[BookId])
    .out(jsonBody[Book])

  val getBooks =
    baseBookEndpoint.get
      .in(Pagination.endpoint)
      .out(
        streamBody(Fs2Streams[Task])(
          Schema(Schema.derived[List[Book]].schemaType),
          CodecFormat.Json()
        )
      )

  val createBook = baseBookEndpoint.put
    .in(jsonBody[NewBook])
    .out(jsonBody[Book].and(statusCode(StatusCode.Created)))

  val createBooks =
    baseBookEndpoint.post.out(jsonBody[Int].and(statusCode(StatusCode.Created)))

  val createBooksWithIterable =
    createBooks.in(jsonBody[Iterable[NewBook]])

  val createBooksWithStream =
    createBooks
      .in(
        streamBody(Fs2Streams[Task])(
          Schema(Schema.derived[List[NewBook]].schemaType),
          CodecFormat.Json()
        )
      )

  val uploadBookImageEndpoint =
    baseBookEndpoint.post
      .in(path[BookId])
      .in("image")
      .in(
        streamBody(Fs2Streams[Task])(
          Schema(Schema.schemaForByteArray.schemaType),
          CodecFormat.OctetStream()
        )
      )
      .out(statusCode(StatusCode.Created))

  val downloadBookImageEndpoint =
    baseBookEndpoint.get
      .in(path[BookId])
      .in("image")
      .out(header[String]("content-type"))
      .out(
        streamBody(Fs2Streams[Task])(
          Schema(Schema.schemaForByteArray.schemaType),
          CodecFormat.OctetStream()
        )
      )

}
