package wow.doge.http4sdemo.server.utils

import cats.effect.Resource
import io.odin.Logger
import monix.bio.Task
import monix.connect.s3.S3
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import wow.doge.http4sdemo.implicits._

object S3ClientResource {
  def apply(url: String)(implicit logger: Logger[Task]) = {

    val credentials = StaticCredentialsProvider.create(
      AwsBasicCredentials.create("minio", "helloworld")
    )
    Resource.make(
      Task(
        S3.createUnsafe(
          credentials,
          Region.AP_SOUTH_1,
          Some(url)
        )
      )
    )(_.close.toIO)
  }
}
