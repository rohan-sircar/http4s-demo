package wow.doge.http4sdemo

import cats.syntax.all._
import io.odin.consoleLogger
import io.odin.fileLogger
import io.odin.syntax._
import monix.bio.Task
import monix.execution.Scheduler

import scala.concurrent.Future
import munit.TestOptions
import cats.effect.Resource
import io.odin.Logger

trait MonixBioSuite extends munit.TaglessFinalSuite[Task] {
  override protected def toFuture[A](f: Task[A]): Future[A] = {
    implicit val s = Scheduler.global
    f.runToFuture
  }

  def loggerFixture(fileName: Option[String] = None)(implicit
      enc: sourcecode.Enclosing
  ) =
    ResourceFixture(
      consoleLogger[Task]().withAsync() |+| fileLogger[Task](
        fileName.getOrElse(enc.value.split("#").head + ".log")
      ),
      (
          options: TestOptions,
          value: Logger[Task]
      ) => Task(options.name),
      (_: Logger[Task]) => Task.unit
    )

}
