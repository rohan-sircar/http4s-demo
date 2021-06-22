package wow.doge.http4sdemo

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.munit.TestContainerForAll
import monix.bio.IO
import monix.bio.Task
import monix.connect.s3.S3
import wow.doge.http4sdemo.server.utils.S3ClientResource

trait MinioItTestBase
    extends MonixBioSuite
    with TestContainerForAll
    with MinioItTestOps {

  override val containerDef: ContainerDef = minioContainerDef

  def withContainersIO[A](f: MinioContainer => Task[A]): Task[A] = {
    withContainers {
      case c: MinioContainer => f(c)
      case c                 => IO.terminate(new Exception(s"Unknown container: ${c.toString}"))
    }
  }

}

trait MinioItTestOps {
  lazy val minioContainerDef = MinioContainer.Def()

  def withS3[T](url: String)(f: S3 => Task[T]) = {
    S3ClientResource(url).use(f)
  }
}
