package wow.doge.http4sdemo

import cats.data.Chain
import cats.effect.concurrent.Ref
import io.odin.Level
import io.odin.Logger
import io.odin.formatter.Formatter
import monix.bio.Task
import wow.doge.http4sdemo.utils.TracingStubLogger

trait ReplayLogger {

  /** Injects a logger that records all log statements but
    * doesn't print by itself. Log statements are only printed
    * if the test fails
    */
  def withReplayLogger(f: Logger[Task] => Task[Unit]) = for {
    chain <- Ref[Task].of(Chain.empty[String])
    testLogger = new TracingStubLogger(chain, Formatter.colorful, Level.Debug)
    _ <- f(testLogger).tapError(err =>
      Task(println("Replaying intercepted logs: ")) >> testLogger.logs.flatMap(
        c => Task(c.iterator.foreach(println))
      )
    )
  } yield ()

}
