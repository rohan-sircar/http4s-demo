package wow.doge.http4sdemo.server.utils

import cats.effect.Resource
import monix.bio.Task
import monix.connect.s3.S3
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.server.config.S3Config

object S3ClientResource {
  def apply(config: S3Config) = {

    val credentials = StaticCredentialsProvider.create(
      AwsBasicCredentials.create(config.akid.value, config.sak.value)
    )
    Resource.make(
      Task(
        S3.createUnsafe(
          credentials,
          Region.AWS_GLOBAL,
          Some(config.endpoint.value)
        )
      )
    )(_.close.toIO)
  }
}
