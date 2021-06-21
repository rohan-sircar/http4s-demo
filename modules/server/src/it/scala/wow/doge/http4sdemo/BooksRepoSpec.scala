package wow.doge.http4sdemo

import java.nio.charset.StandardCharsets

import eu.timepit.refined.auto._
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.reactive.Observable
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.refinements.Refinements
import wow.doge.http4sdemo.server.repos.BookImagesRepoImpl

final class BooksRepoSpec extends MinioItTestBase {

  val bucketName = "library"

  override def afterContainersStart(containers: Containers): Unit = {
    super.afterContainersStart(containers)

    implicit val s = schedulers.io.value

    val task = containers match {
      case c: MinioContainer =>
        withS3(c.rootUrl) { _.createBucket(bucketName).toIO }(
          Logger.noop[Task]
        )
      case other => IO.terminate(new Exception("wrong container type"))
    }
    task.runSyncUnsafe(munitTimeout)
  }

  test("put and get image for id should succeed") {
    withContainersIO { container =>
      withReplayLogger(implicit logger =>
        withS3(container.rootUrl) { s3 =>
          val bookId = Refinements.BookId(1)
          for {
            repo <- BookImagesRepoImpl(s3, bucketName)
            data = Observable.pure(
              "hello world".getBytes(StandardCharsets.UTF_8)
            )
            _ <- repo.put(bookId, data)
            _ <- repo
              .get(bookId)
              .flatMap(
                _.map(bytes =>
                  new String(bytes, StandardCharsets.UTF_8)
                ).firstOptionL.toIO
              )
              .assertEquals(Some("hello world"))
          } yield ()
        }
      )
    }
  }
}
