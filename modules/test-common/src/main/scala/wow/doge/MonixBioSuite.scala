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

trait MonixBioSuite extends munit.TaglessFinalSuite[Task] {
  override protected def toFuture[A](f: Task[A]): Future[A] = {
    implicit val s = Scheduler.global
    f.runToFuture
  }

  val date = LocalDateTime.now()

  val noopLogger = Logger.noop[Task]

  val consoleLogger = io.odin.consoleLogger[Task]()

}
