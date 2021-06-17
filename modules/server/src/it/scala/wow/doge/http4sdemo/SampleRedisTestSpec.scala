package wow.doge.http4sdemo

import monix.bio.Task

final class SampleRedisTestSpec extends RedisItTestBase {
  test("test2") {
    withContainersIO { container =>
      withReplayLogger(implicit logger =>
        withRedis(container.rootUrl) { (ps, redis) =>
          Task.unit
        }
      )
    }
  }
}
