package wow.doge.http4sdemo

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.munit.TestContainerForAll
import dev.profunktor.redis4cats.RedisCommands
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.MonixBioSuite
import wow.doge.http4sdemo.server.config.RedisUrl
import wow.doge.http4sdemo.server.types.RedisStreamEventPs
import wow.doge.http4sdemo.server.utils.RedisResource

trait RedisItTestBase
    extends MonixBioSuite
    with TestContainerForAll
    with RedisTestOps {

  override val containerDef: ContainerDef = redisContainerDef

  def withContainersIO[A](f: RedisContainer => Task[A]): Task[A] = {
    withContainers {
      case c: RedisContainer => f(c)
      case c                 => IO.terminate(new Exception(s"Unknown container: ${c.toString}"))
    }
  }

}

trait RedisTestOps {

  lazy val redisContainerDef = RedisContainer.Def(6379)

  def redisResource(url: RedisUrl)(implicit logger: Logger[Task]) =
    RedisResource(url, logger)

  def withRedis[T](url: RedisUrl)(
      f: (RedisStreamEventPs, RedisCommands[Task, String, String]) => Task[T]
  )(implicit logger: Logger[Task]) = {
    redisResource(url).use { case (ps, redis) =>
      f(ps, redis)
    }
  }
}
