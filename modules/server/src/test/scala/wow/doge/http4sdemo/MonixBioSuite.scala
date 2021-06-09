package wow.doge.http4sdemo

import java.time.LocalDateTime

import scala.concurrent.Future

import monix.bio.Task
import wow.doge.http4sdemo.server.schedulers.Schedulers

trait MonixBioSuite extends munit.TaglessFinalSuite[Task] with ReplayLogger {
  val schedulers = Schedulers.default

  override protected def toFuture[A](f: Task[A]): Future[A] = {
    implicit val s = schedulers.async.value
    f.runToFuture
  }

  val date = LocalDateTime.now()

}
