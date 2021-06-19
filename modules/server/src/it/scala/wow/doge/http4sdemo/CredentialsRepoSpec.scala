package wow.doge.http4sdemo

import eu.timepit.refined.auto._
import monix.bio.Task
import tsec.jws.mac.JWTMac
import tsec.jwt.JWTClaims
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.auth._
import wow.doge.http4sdemo.server.repos.CredentialsRepo
import wow.doge.http4sdemo.server.repos.RedisCredentialsRepo

final class CredentialsRepoSpec extends RedisItTestBase {
  test("put and get credential for id should succeed") {
    withContainersIO { container =>
      withReplayLogger(implicit logger =>
        withRedis(container.rootUrl) { (ps, redis) =>
          // redisCredentialsRepo
          val repo = new RedisCredentialsRepo(redis)(dummySigningKey)
          //   val inMemoryCRepo = InMemoryCredentialsRepo()
          // val testObs = new TestObject()
          val userId = UserId(1)
          for {
            jwt <- JWTMac
              .build[Task, HMACSHA256](
                JWTClaims.default(),
                dummySigningKey.inner
              )
            _ <- repo.put(userId, jwt)
            _ <- repo.get(userId).assertEquals(Some(JwtToken(jwt)))
          } yield ()
        }
      )
    }
  }

  test("remove credential by id should remove the credential") {
    withContainersIO { container =>
      withReplayLogger(implicit logger =>
        withRedis(container.rootUrl) { (ps, redis) =>
          // redisCredentialsRepo
          val repo = new RedisCredentialsRepo(redis)(dummySigningKey)
          //   val inMemoryCRepo = InMemoryCredentialsRepo()
          // val testObs = new TestObject()
          val userId = UserId(2)
          for {
            jwt <- JWTMac
              .build[Task, HMACSHA256](
                JWTClaims.default(),
                dummySigningKey.inner
              )
            _ <- repo.put(userId, jwt)
            _ <- repo.get(userId).assertEquals(Some(JwtToken(jwt)))
            _ <- repo.remove(userId)
            _ <- repo.get(userId).assertEquals(None)
          } yield ()
        }
      )
    }
  }
}

object CredentialsRepoSpec {
  final class TestObject(val name: String, val repo: CredentialsRepo)
}
