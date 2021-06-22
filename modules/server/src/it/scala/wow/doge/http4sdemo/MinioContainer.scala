package wow.doge.http4sdemo

import java.time.Duration

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import wow.doge.http4sdemo.refinements.UrlRefinement

final class MinioContainer private (port: Int, underlying: GenericContainer)
    extends GenericContainer(underlying) {

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def rootUrl = UrlRefinement
    .from(s"http://$host:${mappedPort(port)}")
    .getOrElse(throw new Exception("Invalid url"))
}

object MinioContainer {

  val port = 9000

  final case class Def(version: String = "RELEASE.2021-06-17T00-10-46Z")
      extends GenericContainer.Def[MinioContainer](
        new MinioContainer(
          port,
          GenericContainer(
            dockerImage = s"minio/minio:$version",
            exposedPorts = Seq(port),
            command = Seq("server", "/data"),
            waitStrategy = Wait
              .forHttp("/")
              .forPath("/minio/health/ready")
              .forPort(port)
              .withStartupTimeout(Duration.ofSeconds(10)),
            env = Map(
              "MINIO_ACCESS_KEY" -> "minio",
              "MINIO_SECRET_KEY" -> "helloworld"
            )
          )
        )
      )
}
