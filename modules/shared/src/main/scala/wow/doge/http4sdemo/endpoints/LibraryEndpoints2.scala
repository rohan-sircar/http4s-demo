package wow.doge.http4sdemo.endpoints

// import endpoints4s.{algebra, generic}
import monix.bio.Task
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.codec.newtype._
import sttp.tapir.codec.refined._
import sttp.tapir.json.circe._
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._

/** Defines the HTTP endpoints description of a web service implementing a counter.
  * This web service has two endpoints: one for getting the current value of the counter,
  * and one for incrementing it.
  */
object LibraryEndpoints2 {

  // val addBook: Endpoint[Book, Unit, Unit, Any] = endpoint.post
  //   .in("books")
  //   .in("add")
  //   .in(
  //     jsonBody[Book]
  //       .description("The book to add")
  //       .example(Book("Pride and Prejudice", 1813, Author("Jane Austen")))
  //   )

  // statusM

  val baseEndpoint = errorEndpoint.in("api" / "books")

  // val baseEndpoint = endpoint.errorOut(
  //   oneOf[ErrorInfo](
  //     statusMapping(
  //       StatusCode.NotFound,
  //       jsonBody[NotFound].description("not found")
  //     ),
  //     statusMapping(
  //       StatusCode.Unauthorized,
  //       jsonBody[Unauthorized].description("unauthorized")
  //     ),
  //     statusMapping(
  //       StatusCode.NoContent,
  //       emptyOutput.map(_ => NoContent)(_ => ())
  //     ),
  //     statusDefaultMapping(jsonBody[Unknown].description("unknown"))
  //   )
  // )

  val getBookById =
    baseEndpoint.get
      .in(path[BookId])
      // .in(extractFromRequest(_.header("X-request-id")))
      .out(jsonBody[Book])
  // .errorOut(jsonBody[AppError2].and(statusCode(StatusCode.NotFound)))
  // .errorOut(jsonBody[AppError2])

  // query[PaginationLimit]("limit").and(query[PaginationPage]("page")).map {
  //   case (limit, page) => Pagination(page, limit)
  // }(p => (p.limit, p.page))

  // endpoint.in

  val getBooks =
    baseEndpoint.get
      // .in(query[PaginationLimit]("limit").and(query[PaginationPage]("page")))
      .in(Pagination.endpoint)
      .out(
        streamBody(Fs2Streams[Task])(
          Schema(Schema.derived[List[Book]].schemaType),
          CodecFormat.Json()
        )
      )

//   val createBook: Endpoint[NewBook, Either[Book, ClientErrors]] =
//     endpoint(
//       put(path / "api" / "books", jsonRequest[NewBook]),
//       ok(jsonResponse[Book]).orElse(badRequest())
//     )

//   val getBooks: Endpoint[(PaginationLimit, PaginationPage), Chunks[Book]] =
//     endpoint(
//       get(
//         path / "api" / "books" /? (qs[PaginationLimit]("limit") &
//           qs[PaginationPage]("page"))
//       ),
//       ok(jsonChunksResponse[Book])
//     )

//   val createBooks: Endpoint[Chunks[NewBook], Either[NumRows, ClientErrors]] =
//     endpoint(
//       post(path / "api" / "books", jsonChunksRequest[NewBook]),
//       ok(jsonResponse[NumRows]).orElse(badRequest())
//     )

//   val searchBooks
//       : Endpoint[(PaginationLimit, PaginationPage, SearchQuery), Chunks[Book]] =
//     endpoint(
//       get(
//         path / "api" / "books" / "search" /? (qs[PaginationLimit]("limit") &
//           qs[PaginationPage]("page") & qs[SearchQuery]("q"))
//       ),
//       ok(jsonChunksResponse[Book])
//     )

//   /** Increments the counter value.
//     * Uses the HTTP verb “POST” and URL path “/increment”.
//     * The request entity is a JSON document representing the increment to apply to the counter.
//     * The response entity is empty.
//     */
// //   val increment: Endpoint[Increment, Unit] =
// //     endpoint(
// //       post(path / "increment", jsonRequest[Increment]),
// //       ok(emptyResponse)
// //     )

}
