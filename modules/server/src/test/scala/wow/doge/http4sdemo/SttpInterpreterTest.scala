package wow.doge.http4sdemo

import eu.timepit.refined.auto._
import monix.bio.IO
import monix.bio.Task
import sttp.client3._
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.client.sttp.ws.fs2._
import wow.doge.http4sdemo.endpoints.LibraryEndpoints
import wow.doge.http4sdemo.endpoints.ReqContext
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.pagination.Pagination
import wow.doge.http4sdemo.models.pagination.PaginationLimit
import wow.doge.http4sdemo.models.pagination.PaginationPage
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.utils.observableFromByteStreamA

@munit.IgnoreSuite
class SttpInterpreterTest extends MonixBioSuite {
//   lazy val fixture = ResourceFixture.apply(
//     Resource.make(HttpClientMonixBackend()(schedulers.async.value).toIO)(
//       _.close().toIO
//     )
//   )

  lazy val fixture = {
    implicit val s = schedulers.async.value
    ResourceFixture.apply(
      HttpClientFs2Backend.resource[Task](schedulers.io.blocker)
    )

  }
  fixture.test("get book by id") { backend =>
    for {
      _ <- Task.unit
      req =
        SttpClientInterpreter.toClientThrowErrors(
          LibraryEndpoints.getBookById,
          Some(uri"http://localhost:8081/"),
          backend
        )
      res <- req((ReqContext.empty, BookId(1)))
      _ <- IO(println(res))
    } yield ()
  }
  fixture.test("get books") { backend =>
    for {
      _ <- Task.unit
      req =
        SttpClientInterpreter.toClientThrowErrors(
          LibraryEndpoints.getBooks,
          Some(uri"http://localhost:8081/"),
          backend
        )
      stream <- req(
        (ReqContext.empty, Pagination(PaginationPage(0), PaginationLimit(5)))
      )
      obs <- observableFromByteStreamA[Book](stream)
      lst <- obs.toListL.toIO
      // .compile
      // .toList
      // .map(_.sequence)
      // obs <- Media(stream, Headers.empty).as[Observable[Json]]
      // lst <- obs.mapEvalF(_.as[Book]).toListL.toIO
      _ <- IO(println(lst))

      // r = res
      //   .map(
      //     _.map(stream =>
      //       observableArrayJsonDecoder[Task]
      //         .decode(Media[Task](stream, Headers.empty), true)
      //         .map(_.map(_.as[Book]))
      //     )
      //   )
      //   .flatMap(r => IO(println(r)))
    } yield ()
  }
  fixture.test("create books") { backend =>
    for {
      _ <- Task.unit
      req =
        SttpClientInterpreter.toClientThrowErrors(
          LibraryEndpoints.createBooksWithIterable,
          Some(uri"http://localhost:8081/"),
          backend
        )
      res <- req(
        (
          ReqContext.empty,
          List(NewBook(BookTitle("asfawq"), BookIsbn("adbqegqw"), AuthorId(1)))
        )
      )
      _ <- IO(println(res))
    } yield ()
  }
}
