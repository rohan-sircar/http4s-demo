package wow.doge.http4sdemo

import eu.timepit.refined.auto._
import monix.bio.IO
import monix.bio.Task
import monix.reactive.Observable
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.StreamInputEvent
import wow.doge.http4sdemo.server.utils.observableToJsonStreamA

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
  // fixture.test("get book by id") { backend =>
  //   for {
  //     _ <- Task.unit
  //     req =
  //       SttpClientInterpreter.toClientThrowErrors(
  //         LibraryEndpoints.getBookById,
  //         Some(uri"http://localhost:8081/"),
  //         backend
  //       )
  //     res <- req((ReqContext.empty, BookId(1)))
  //     _ <- IO(println(res))
  //   } yield ()
  // }
  // fixture.test("get books") { backend =>
  //   for {
  //     _ <- Task.unit
  //     req =
  //       SttpClientInterpreter.toClientThrowErrors(
  //         LibraryEndpoints.getBooks,
  //         Some(uri"http://localhost:8081/"),
  //         backend
  //       )
  //     stream <- req(
  //       (ReqContext.empty, Pagination(PaginationPage(0), PaginationLimit(5)))
  //     )
  //     obs <- observableFromByteStreamA[Book](stream)
  //     lst <- obs.toListL.toIO
  //     // .compile
  //     // .toList
  //     // .map(_.sequence)
  //     // obs <- Media(stream, Headers.empty).as[Observable[Json]]
  //     // lst <- obs.mapEvalF(_.as[Book]).toListL.toIO
  //     _ <- IO(println(lst))

  //     // r = res
  //     //   .map(
  //     //     _.map(stream =>
  //     //       observableArrayJsonDecoder[Task]
  //     //         .decode(Media[Task](stream, Headers.empty), true)
  //     //         .map(_.map(_.as[Book]))
  //     //     )
  //     //   )
  //     //   .flatMap(r => IO(println(r)))
  //   } yield ()
  // }
  // fixture.test("create books") { backend =>
  //   for {
  //     _ <- Task.unit
  //     req =
  //       SttpClientInterpreter.toClientThrowErrors(
  //         LibraryEndpoints.createBooksWithIterable,
  //         Some(uri"http://localhost:8081/"),
  //         backend
  //       )
  //     res <- req(
  //       (
  //         ReqContext.empty,
  //         List(NewBook(BookTitle("asfawq"), BookIsbn("adbqegqw"), AuthorId(1)))
  //       )
  //     )
  //     _ <- IO(println(res))
  //   } yield ()
  // }

  fixture.test("http request stream") { implicit backend =>
    import scala.concurrent.duration._
    for {
      _ <- IO.unit
      steambase <- Observable.interval(2.seconds).toStreamIO
      _ <- basicRequest
        .post(uri"http://localhost:8081/api/publish")
        .streamBody(Fs2Streams[Task])(
          steambase
            .flatMap(x =>
              fs2.Stream.fromIterator[Task](x.toString.getBytes.iterator)
            )
        )
        .send(backend)
    } yield ()

  }

  fixture.test("events stream") { implicit backend =>
    import scala.concurrent.duration._
    for {
      _ <- IO.unit
      steambase <- observableToJsonStreamA(
        Observable
          .interval(2.seconds)
          .map(i =>
            StreamInputEvent
              .Message2(i, i.toInt + 1, s"foobar: $i"): StreamInputEvent
          )
      )
      _ <- basicRequest
        .post(uri"http://localhost:8081/api/publish2")
        .streamBody(Fs2Streams[Task])(steambase)
        .send(backend)
    } yield ()

  }
}
