package wow.doge.http4sdemo.endpoints

import monix.bio.Task
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.tapir.CodecFormat
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.refinements._
import wow.doge.http4sdemo.utils.mytapir._
object LibraryEndpoints {

  val baseBookEndpoint = baseEndpoint.in("books")

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

  val createBook = baseBookEndpoint.post
    .in(jsonBody[NewBook])
    .out(jsonBody[Book].and(statusCode(StatusCode.Created)))

  val createBooks =
    baseBookEndpoint.post
      .in("stream")
      .out(jsonBody[Int].and(statusCode(StatusCode.Created)))

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

  val searchBooksEndpoint =
    baseBookEndpoint.get
      .in("search")
      .in(query[BookSearchMode]("mode"))
      // .in(Pagination.endpoint)
      .in(query[SearchQuery]("q"))
      .out(
        streamBody(Fs2Streams[Task])(
          Schema(Schema.derived[List[Book]].schemaType),
          CodecFormat.Json()
        )
      )

  val deleteBookEndpoint = basePrivateEndpoint
    .in("books")
    .delete
    .in(path[BookId])
    .out(statusCode(StatusCode.NoContent))

  val updateBookEndpoint: Endpoint[
    (ReqContext, AuthDetails, BookId, BookUpdate),
    AppError,
    Unit,
    Any
  ] =
    basePrivateEndpoint
      .in("books")
      .patch
      .in(path[BookId])
      .in(jsonBody[BookUpdate])
      .out(statusCode(StatusCode.NoContent))

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
