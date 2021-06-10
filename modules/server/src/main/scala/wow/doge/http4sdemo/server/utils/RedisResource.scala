package wow.doge.http4sdemo.server.utils

import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data
import dev.profunktor.redis4cats.log4cats._
import eu.timepit.refined.types.string
import io.odin.Logger
import monix.bio.Task
import org.typelevel.log4cats.{Logger => L4sLogger}
import wow.doge.http4sdemo.server.config.RedisUrl
import wow.doge.http4sdemo.server.utils.StructuredOdinLogger2

object RedisResource {
  def apply(
      url: RedisUrl,
      logger: Logger[Task]
  ) = {
    implicit val l4s: L4sLogger[Task] =
      new StructuredOdinLogger2[Task](logger, "redisClient")
    for {
      client <- RedisClient[Task].from(url.inner.value)
      cmd <- Redis[Task].fromClient(client, data.RedisCodec.Utf8)
    } yield cmd
  }
}
