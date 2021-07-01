package wow.doge.http4sdemo

import cats.data.Chain
import cats.effect.concurrent.Ref
import cats.syntax.all._
import emil.Mail
import eu.timepit.refined.auto._
import io.chrisdavenport.fuuid.FUUID
import io.odin.Logger
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.circe.CirceEntityCodec._
import wow.doge.http4sdemo.endpoints.LoginResponse
import wow.doge.http4sdemo.endpoints.RegistrationResponse
import wow.doge.http4sdemo.endpoints.basePrivateEndpoint
import wow.doge.http4sdemo.models.User
import wow.doge.http4sdemo.models.UserLogin
import wow.doge.http4sdemo.models.UserRegistration
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.UnitTestBase
import wow.doge.http4sdemo.server.repos.InMemoryAccountActivationTokensRepo
import wow.doge.http4sdemo.server.repos.InMemoryUsersRepo
import wow.doge.http4sdemo.server.routes.AccountRoutes
import wow.doge.http4sdemo.server.routes.AuthedServerInterpreter
import wow.doge.http4sdemo.server.services.AuthService
import wow.doge.http4sdemo.server.services.TestAuthService
import wow.doge.http4sdemo.server.services.UserServiceImpl
import wow.doge.http4sdemo.server.utils.MailClient
import wow.doge.http4sdemo.server.utils.SpyMailClient
import wow.doge.http4sdemo.utils.mytapir._

final class RegistrationFlowSpec extends UnitTestBase {

  val registration =
    UserRegistration(
      Username("foobar1"),
      UnhashedUserPassword("barfoo1"),
      UserEmail("rohansircar@protonmail.com")
    )
  val login = registration.transformInto[UserLogin]

  test("registration flow should succeed for regular user") {

    withReplayLogger { implicit logger =>
      for {
        usersRepo <- InMemoryUsersRepo()
        authService <- TestAuthService(Some(usersRepo))(dummySigningKey)
        mcRef <- Ref[Task].of(Chain.empty[(FUUID, Mail[Task])])
        mc = new SpyMailClient(MailClient.dummyConfig, mcRef, logger)
        aatrRef <- Ref[Task].of(Map.empty[FUUID, User])
        aatr = new InMemoryAccountActivationTokensRepo(aatrRef)
        userService = new UserServiceImpl(usersRepo, mc, aatr)
        routes = new TestAuthedRoutes(authService, logger).routes <+>
          new AccountRoutes(authService, userService)(logger).routes

        request = Request[Task](Method.POST, Root / "api" / "register")
          .withEntity(registration)
        res <- routes.run(request).value
        body <- res.traverse(_.as[RegistrationResponse])
        _ <- logger.debug(s"Request: $request, Response: $res, Body: $body")
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Created))
        id <- IO.fromOption(
          body.map(_.userId),
          new Exception("Failed to get user id")
        )

        _ <- mcRef.get
          .map(_.size)
          .assertEquals(1L, "Did not send verification mail after registration")

        verToken <- aatrRef.get
          .map(_.toList.headOption.map { case (token, _) =>
            token
          })
          .flatMap(t =>
            IO.fromOption(t, new Exception("failed to get verification token"))
          )

        request = Request[Task](Method.POST, Root / "api" / "login")
          .withEntity(login)
        res <- routes.run(request).value
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Unauthorized))
        body <- res
          .traverse(_.as[AppError])
          .assertEquals(Some(AppError.AuthError("Account not activated")))

        request = Request[Task](
          Method.POST,
          Root / "api" / "activate-account" / verToken.show
        )
        res <- routes.run(request).value
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Ok))

        _ <- aatrRef.get
          .map(_.size)
          .assertEquals(
            0,
            "Did not clear verification token after verification"
          )

        request = Request[Task](Method.POST, Root / "api" / "login")
          .withEntity(login)
        res <- routes.run(request).value
        body <- res.traverse(_.as[LoginResponse])
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Ok))
        token <- IO.fromOption(
          body.map(_.token),
          new Exception("failed to get auth token")
        )

        request = Request[Task](
          Method.GET,
          Root / "api" / "private" / "regular-user"
        ).withHeaders(authHeaderFromTokenStr(token))
        res <- routes.run(request).value
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Ok))

        request = Request[Task](
          Method.GET,
          Root / "api" / "private" / "admin"
        ).withHeaders(authHeaderFromTokenStr(token))
        res <- routes.run(request).value
        _ <- IO(res.map(_.status)).assertEquals(Some(Status.Unauthorized))
      } yield ()
    }
  }
}

final class TestAuthedRoutes(
    val authService: AuthService,
    val logger: Logger[Task]
) extends AuthedServerInterpreter {

  def authedRoute(uri: String, role: UserRole) = {
    toRoutes(
      basePrivateEndpoint.get
        .in(uri)
        .out(stringBody)
        .serverLogicRecoverErrors { case (ctx, details) =>
          authorize(ctx, details)(role) { case (logger, authDetails) =>
            IO.pure("hello")
          }.leftWiden[Throwable]
        }
    )
  }

  val regularUserRoute = authedRoute("regular-user", UserRole.User)

  val adminRoute = authedRoute("admin", UserRole.Admin)

  val superUserRoute = authedRoute("super-user", UserRole.SuperUser)

  val routes = regularUserRoute <+> adminRoute <+> superUserRoute
}
