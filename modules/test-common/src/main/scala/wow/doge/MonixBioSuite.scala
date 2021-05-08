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
  def loggerInterceptor(f: utils.Logger => Task[Unit]) = for {
    stack <- Ref[Task].of(List.empty[String])
    testLogger = new TracingStubLogger(stack)
    _ <- f(testLogger).tapError(err =>
      stack.get.flatMap(lst => Task(lst.foreach(println)))
    )
  } yield ()

}
