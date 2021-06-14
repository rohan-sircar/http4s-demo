// package wow.doge.http4sdemo.server.routes
// import $file.^.^.^.^.^.^.^.^.target.`scala-2.13`.`fullClasspath-Compile`
import $file.^.modules.server.target.`scala-2.13`.`fullClasspath-Compile`

import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.WebSockets
import monix.execution.Scheduler
import wow.doge.http4sdemo.models.StreamInputEvent
import monix.reactive.Observable
import cats.effect.Resource
import monix.bio.Task
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import wow.doge.http4sdemo.server.concurrent.Schedulers
import scala.concurrent.duration._
import sttp.client3._
import wow.doge.http4sdemo.server.utils.observableToJsonStreamA
import wow.doge.http4sdemo.models.StreamEvent
import wow.doge.http4sdemo.implicits._
import monix.catnap.ConcurrentQueue
import wow.doge.http4sdemo.utils.observableFromByteStreamA

val schedulers = Schedulers.default

implicit val s = schedulers.async.value

val backendResource = HttpClientFs2Backend.resource[Task](schedulers.io.blocker)

val program: Resource[Task, Unit] = for {
  backend <- backendResource
  _ = Resource.eval(for {
    queue <- ConcurrentQueue[Task].bounded[StreamInputEvent](50)
    _ <- Task.parSequence(
      List(
        Observable
          .interval(2.seconds)
          .map(i =>
            StreamInputEvent
              .Message2(i, i.toInt + 1, s"foobar: $i"): StreamInputEvent
          )
          .doOnNext(event => queue.offer(event).toTask)
          .completedL
          .toIO,
        observableToJsonStreamA(Observable.repeatEvalF(queue.poll)).flatMap(o =>
          basicRequest
            .post(uri"http://localhost:8081/api/publish2")
            .streamBody(Fs2Streams[Task])(o)
            .send(backend)
        )
      )
    )
  } yield ())
  _ <- Resource.make(
    Task
      .parSequence(
        List(
          (for {
            queue <- ConcurrentQueue[Task].bounded[StreamInputEvent](50)
            _ <- Task.parSequence(
              List(
                Observable
                  .interval(2.seconds)
                  .map(i =>
                    StreamInputEvent
                      .Message2(i, i.toInt + 1, s"foobar: $i"): StreamInputEvent
                  )
                  .doOnNext(event => queue.offer(event).toTask)
                  .completedL
                  .toIO,
                observableToJsonStreamA(
                  Observable.repeatEvalF(queue.poll)
                ).flatMap(o =>
                  basicRequest
                    .post(uri"http://localhost:8081/api/publish2")
                    .streamBody(Fs2Streams[Task])(o)
                    .send(backend)
                )
              )
            )
          } yield ()),
          (for {
            queue <- ConcurrentQueue[Task].bounded[StreamEvent](50)
            _ <- Task.parSequence(
              List(
                basicRequest
                  .get(uri"http://localhost:8081/api/subscribe2")
                  .response(
                    asStream(Fs2Streams[Task])(byteStream =>
                      for {
                        obs <- observableFromByteStreamA[StreamEvent](
                          byteStream
                        )
                        _ <- obs
                          .doOnNext(event => queue.offer(event).toTask)
                          .completedL
                          .toIO
                      } yield ()
                    )
                  )
                  .send(backend),
                Observable
                  .repeatEvalF(queue.poll)
                  .doOnNext(event =>
                    Task(pprint.log(s"Got event $event")).toTask.void
                  )
                  .completedL
                  .toIO
              )
            )
          } yield ())
        )
      )
      .start
  )(_.cancel)
} yield ()

@main def run() = program
  .use(_ => Task(pprint.log("Started")) >> Task.sleep(10.seconds))
  .runSyncUnsafe()

// println("helloo")
