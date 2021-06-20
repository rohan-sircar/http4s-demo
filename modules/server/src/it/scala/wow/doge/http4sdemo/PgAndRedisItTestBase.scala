package wow.doge.http4sdemo

import cats.effect.Resource
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.lifecycle.and
import com.dimafeng.testcontainers.munit.TestContainersForAll
import dev.profunktor.redis4cats.RedisCommands
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import slick.jdbc.JdbcBackend
import wow.doge.http4sdemo.server.config.RedisUrl
import wow.doge.http4sdemo.server.types._

trait PgAndRedisItTestBase
    extends MonixBioSuite
    with TestContainersForAll
    with RedisTestOps
    with PgItTestOps {
  val databaseName = "testcontainer-scala"
  val username = "scala"
  val password = "scala"

  override type Containers = PostgreSQLContainer and RedisContainer

  override def startContainers(): PostgreSQLContainer and RedisContainer = {
    val pgContainer = pgContainerDef.start()
    val redisContainer = redisContainerDef.start()
    pgContainer and redisContainer
  }

  def withContainersIO[A](
      f: (PostgreSQLContainer, RedisContainer) => Task[A]
  ): Task[A] = {
    withContainers {
      case pgContainer and redisContainer => f(pgContainer, redisContainer)
      case c                              => IO.terminate(new Exception(s"Unknown container: ${c.toString}"))
    }
  }

  def withPgAndRedis[T](pgUrl: String, redisUrl: RedisUrl)(
      f: (
          JdbcBackend.DatabaseDef,
          RedisStreamEventPs,
          RedisCommands[Task, String, String]
      ) => Task[T]
  )(implicit logger: Logger[Task]) = {
    val r = for {
      (ps, redis) <- redisResource(redisUrl)
      db <- dbResource(pgUrl)
      _ <- Resource.eval(f(db, ps, redis))
    } yield ()
    r.use(_ => Task.unit)
  }

}
