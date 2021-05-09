package wow.doge.http4sdemo

import scala.concurrent.Future

import cats.syntax.all._
import io.odin.Logger
import io.odin.fileLogger
import io.odin.syntax._
import monix.bio.Task
import monix.execution.Scheduler
import munit.TestOptions
import java.time.LocalDateTime
import cats.effect.concurrent.Ref
import io.odin.formatter.Formatter
import io.odin.Level
import monix.reactive.Observable
import monix.{eval => me}
import cats.data.Chain
import wow.doge.http4sdemo.utils.TracingStubLogger

trait MonixBioSuite extends munit.TaglessFinalSuite[Task] {
  override protected def toFuture[A](f: Task[A]): Future[A] = {
    implicit val s = Scheduler.global
    f.runToFuture
  }

  val date = LocalDateTime.now()

  /** Injects a logger that records all log statements but
    * doesn't print by itself. Log statements are only printed
    * if the test fails
    */
  def loggerInterceptor(f: Logger[Task] => Task[Unit]) = for {
    chain <- Ref[Task].of(Chain.empty[String])
    testLogger = new TracingStubLogger(chain, Formatter.colorful, Level.Debug)
    _ <- f(testLogger).tapError(err =>
      Task(println("Replaying intercepted logs: ")) >> chain.get.flatMap(c =>
        Task(c.iterator.foreach(println))
      )
    )
  } yield ()

}
