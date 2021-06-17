package wow.doge.http4sdemo

import com.dimafeng.testcontainers.lifecycle._
import monix.bio.IO
import monix.bio.Task

final class SamplePgAndRedisItTest extends PgAndRedisItTestBase {

  override def afterContainersStart(containers: Containers): Unit = {
    implicit val s = schedulers.io.value
    super.afterContainersStart(containers)
    val io = containers match {
      case pgContainer and redisContainer => createSchema(pgContainer)
      case c                              => IO.terminate(new Exception("boom"))
    }
    io.runSyncUnsafe(munitTimeout)
  }

  test("foobar") {
    withReplayLogger(implicit logger =>
      withContainersIO { (pgContainer, redisContainer) =>
        withPgAndRedis(pgContainer.jdbcUrl, redisContainer.rootUrl)(
          (db, ps, redis) => Task.unit
        )
      }
    )
  }
}
