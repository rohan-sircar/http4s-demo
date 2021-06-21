package wow.doge.http4sdemo

import com.dimafeng.testcontainers.GenericContainer
import wow.doge.http4sdemo.server.config.RedisUrl

final class RedisContainer private (port: Int, underlying: GenericContainer)
    extends GenericContainer(underlying) {

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def rootUrl = RedisUrl
    .parseStr(s"redis://$host:${mappedPort(port)}")
    .getOrElse(throw new Exception("boom"))
}

object RedisContainer {

  // In the container definition you need to describe, how your container will be constructed:
  final case class Def(version: String = "5-alpine")
      extends GenericContainer.Def[RedisContainer](
        new RedisContainer(
          6379,
          GenericContainer(
            dockerImage = s"redis:$version",
            exposedPorts = Seq(6379)
          )
        )
      )
}
