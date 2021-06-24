package wow.doge.http4sdemo

import eu.timepit.refined.auto._
import fs2.Chunk
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.refinements.Refinements
import wow.doge.http4sdemo.server.repos.BookImagesRepoImpl
import wow.doge.http4sdemo.server.utils.ImageStream

final class BookImagesRepoSpec extends MinioItTestBase {

  val bucketName = "library"

  override def afterContainersStart(containers: Containers): Unit = {
    super.afterContainersStart(containers)

    implicit val s = schedulers.io.value

    val task = containers match {
      case c: MinioContainer =>
        withS3(c.rootUrl) { _.createBucket(bucketName).toIO }
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
            dataBytes = Chunk.array(
              os.read.bytes(os.resource / "images" / "JME.png")
            )
            iStream <- ImageStream.parse(
              fs2.Stream.chunk[Task, Byte](dataBytes)
            )
            _ <- repo.put(bookId, iStream)
            _ <- repo
              .get(bookId)
              .flatMap(_.obs.firstOptionL.toIO)
              .assertEquals(Some(dataBytes))
          } yield ()
        }
      )
    }
  }
}
