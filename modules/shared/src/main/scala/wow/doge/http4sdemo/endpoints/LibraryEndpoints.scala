package wow.doge.http4sdemo.endpoints

import endpoints4s.algebra
import endpoints4s.generic
import io.estatico.newtype.ops._
import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.refinements._

/** Defines the HTTP endpoints description of a web service implementing a counter.
  * This web service has two endpoints: one for getting the current value of the counter,
  * and one for incrementing it.
  */
trait LibraryEndpoints
    extends algebra.Endpoints
    with algebra.JsonEntitiesFromSchemas
    with generic.JsonSchemas
    with algebra.ChunkedJsonEntities
    with RefinedJsonSchemas
    with RefinedUrls {

  /** Get the counter current value.
    * Uses the HTTP verb “GET” and URL path “/current-value”.
    * The response entity is a JSON document representing the counter value.
    */
//   val currentValue: Endpoint[Unit, Counter] =
//     endpoint(get(path / "current-value"), ok(jsonResponse[Counter]))

  val getBookById: Endpoint[BookId, Option[Book]] =
    endpoint(
      get(path / "api" / "books" / segment[BookId]()),
      ok(jsonResponse[Book]).orNotFound()
    )

  val createBook: Endpoint[NewBook, Either[Book, ClientErrors]] =
    endpoint(
      put(path / "api" / "books", jsonRequest[NewBook]),
      ok(jsonResponse[Book]).orElse(badRequest())
    )

  val getBooks: Endpoint[(PaginationLimit, PaginationPage), Chunks[Book]] =
    endpoint(
      get(
        path / "api" / "books" /? (qs[PaginationLimit]("limit") &
          qs[PaginationPage]("page"))
      ),
      ok(jsonChunksResponse[Book])
    )

  val createBooks: Endpoint[Chunks[NewBook], Either[NumRows, ClientErrors]] =
    endpoint(
      post(path / "api" / "books", jsonChunksRequest[NewBook]),
      ok(jsonResponse[NumRows]).orElse(badRequest())
    )

  val searchBooks
      : Endpoint[(PaginationLimit, PaginationPage, SearchQuery), Chunks[Book]] =
    endpoint(
      get(
        path / "api" / "books" / "search" /? (qs[PaginationLimit]("limit") &
          qs[PaginationPage]("page") & qs[SearchQuery]("q"))
      ),
      ok(jsonChunksResponse[Book])
    )

  /** Increments the counter value.
    * Uses the HTTP verb “POST” and URL path “/increment”.
    * The request entity is a JSON document representing the increment to apply to the counter.
    * The response entity is empty.
    */
//   val increment: Endpoint[Increment, Unit] =
//     endpoint(
//       post(path / "increment", jsonRequest[Increment]),
//       ok(emptyResponse)
//     )

  // Generically derive the JSON schema of our `Counter`
  // and `Increment` case classes defined thereafter
//   implicit lazy val counterSchema: JsonSchema[Counter] = genericJsonSchema
//   implicit lazy val incrementSchema: JsonSchema[Increment] = genericJsonSchema

  implicit lazy val schemaBookId: JsonSchema[BookId] =
    implicitly[JsonSchema[IdRefinement]].coerce[JsonSchema[BookId]]
  implicit lazy val schemaBookTitle: JsonSchema[BookTitle] =
    implicitly[JsonSchema[StringRefinement]].coerce[JsonSchema[BookTitle]]
  implicit lazy val schemaBookIsbn: JsonSchema[BookIsbn] =
    implicitly[JsonSchema[StringRefinement]].coerce[JsonSchema[BookIsbn]]
  implicit lazy val schemaAuthorId: JsonSchema[AuthorId] =
    implicitly[JsonSchema[IdRefinement]].coerce[JsonSchema[AuthorId]]
  implicit lazy val schemaAuthorName: JsonSchema[AuthorName] =
    implicitly[JsonSchema[StringRefinement]].coerce[JsonSchema[AuthorName]]

  implicit lazy val schemaNumRows: JsonSchema[NumRows] =
    implicitly[JsonSchema[Int]].coerce[JsonSchema[NumRows]]

  implicit lazy val schemaBook: JsonSchema[Book] = genericJsonSchema
  implicit lazy val schemaNewBook: JsonSchema[NewBook] = genericJsonSchema
  implicit lazy val schemaAuthor: JsonSchema[Author] = genericJsonSchema
  implicit lazy val schemaNewAuthor: JsonSchema[NewAuthor] = genericJsonSchema

  implicit lazy val segmentBookId: Segment[BookId] =
    implicitly[Segment[IdRefinement]].coerce[Segment[BookId]]

  implicit lazy val qsPaginationLimit: QueryStringParam[PaginationLimit] =
    implicitly[QueryStringParam[PaginationRefinement]]
      .coerce[QueryStringParam[PaginationLimit]]

  implicit lazy val qsPaginationPage: QueryStringParam[PaginationPage] =
    implicitly[QueryStringParam[PaginationRefinement]]
      .coerce[QueryStringParam[PaginationPage]]

}
