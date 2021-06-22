package wow.doge.http4sdemo.server.repos

import fs2.Chunk
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.connect.s3.S3
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.utils.ImageStream
import wow.doge.http4sdemo.utils.infoSpan

trait BookImagesRepo {
  def put(id: BookId, imageStream: ImageStream)(implicit
      logger: Logger[Task]
  ): Task[Unit]

  def get(id: BookId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, ImageStream]
}

final class BookImagesRepoImpl private (s3: S3, bucketName: String)
    extends BookImagesRepo {
  private def key(id: BookId) = s"books/$id/image"

  def put(id: BookId, imageStream: ImageStream)(implicit
      logger: Logger[Task]
  ) = infoSpan {
    for {
      _ <- imageStream.obs
        .map(_.toArray)
        .consumeWith(s3.uploadMultipart(bucketName, key(id)))
        .toIO
    } yield ()
  }

  def get(id: BookId)(implicit logger: Logger[Task]) = infoSpan {
    ImageStream.parse(
      s3.downloadMultipart(bucketName, key(id)).map(Chunk.array[Byte])
    )
  }
}

object BookImagesRepoImpl {

  def apply(s3: S3, bucketName: String) = for {
    exists <- s3.existsBucket(bucketName).toIO.hideErrors
    _ <-
      if (!exists)
        IO.raiseError(
          new AppError2.EntityDoesNotExist(
            s"Bucket with name: $bucketName does not exist"
          )
        )
      else IO.unit
  } yield new BookImagesRepoImpl(s3, bucketName)
}

class NoopBookImagesRepo extends BookImagesRepo {
  def put(id: BookId, imageStream: ImageStream)(implicit
      logger: Logger[Task]
  ): Task[Unit] = Task.raiseError(new NotImplementedError)

  def get(id: BookId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, ImageStream] =
    IO.terminate(new NotImplementedError)
}
