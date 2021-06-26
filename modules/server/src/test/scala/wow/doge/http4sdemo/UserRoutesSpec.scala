package wow.doge.http4sdemo

import cats.syntax.all._
import monix.bio.IO
import monix.bio.Task
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import wow.doge.http4sdemo.models.UserIdentity
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.server.UnitTestBase
import wow.doge.http4sdemo.server.repos.InMemoryUsersRepo
import wow.doge.http4sdemo.server.routes.UserRoutes
import wow.doge.http4sdemo.server.services.AuthServiceImpl
import wow.doge.http4sdemo.server.services.NoOpAuthService
import wow.doge.http4sdemo.server.services.NoopUserService
import wow.doge.http4sdemo.server.services.UserServiceImpl

final class UserRoutesSpec extends UnitTestBase {
  test("get user api should return current user identity for authed user") {
    import org.http4s.circe.CirceEntityCodec._
    withReplayLogger { implicit logger =>
      for {
        usersRepo <- InMemoryUsersRepo()
        (id, token, authService) <- AuthServiceImpl
          .inMemoryWithUserWithoutInvalidator(regularNewUser)(
            dummySigningKey,
            logger
          )
        userService = new UserServiceImpl(usersRepo)
        routes = new UserRoutes(userService, authService)(logger).getUserRoute
        request = Request[Task](
          Method.GET,
          Root / "api" / "private" / "user"
        ).withHeaders(authHeader(token))
        res <- routes.run(request).value
        body <- res.traverse(_.as[UserIdentity])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Ok))
        _ <- IO(
          assertEquals(
            body,
            Some(UserIdentity(id, regularNewUser.username, UserRole.user))
          )
        )
      } yield ()
    }
  }

  test(
    "get user api return unauthorized status code when accessed by un-authed user"
  ) {
    withReplayLogger { implicit logger =>
      for {
        _ <- IO.unit
        userService = new NoopUserService
        authService = new NoOpAuthService
        routes = new UserRoutes(userService, authService)(logger).getUserRoute
        request = Request[Task](
          Method.GET,
          Root / "api" / "private" / "user"
        )
        res <- routes.run(request).value
        _ <- logger.debug(s"Request: $request")
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Unauthorized))
      } yield ()
    }
  }
}
