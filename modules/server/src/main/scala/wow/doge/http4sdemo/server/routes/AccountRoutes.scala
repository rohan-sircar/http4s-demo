package wow.doge.http4sdemo.server.routes

import cats.syntax.all._
import io.chrisdavenport.fuuid.FUUID
import io.odin.Logger
import io.odin.syntax._
import monix.bio.Task
import wow.doge.http4sdemo.endpoints.AccountEndpoints
import wow.doge.http4sdemo.endpoints.LoginResponse
import wow.doge.http4sdemo.endpoints.RegistrationResponse
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.UserLogin
import wow.doge.http4sdemo.models.UserRegistration
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.server.services.AuthService
import wow.doge.http4sdemo.server.services.UserService
import wow.doge.http4sdemo.utils.infoSpan

final class AccountRoutes(
    val authService: AuthService,
    U: UserService
    // aatr: AccountActivationTokensRepo
)(
    val logger: Logger[Task]
) extends AuthedServerInterpreter {

  def login(user: UserLogin)(implicit logger: Logger[Task]) = infoSpan {
    for {
      _ <- logger.debugU(s"Logging in")
      jwt <- authService.login(user)
      res = LoginResponse(jwt.toEncodedString)
    } yield res
  }

  val loginRoute = toRoutes(
    AccountEndpoints.loginEndpoint
      .serverLogicPart(enrichLogger)
      .andThenRecoverErrors { case (logger, user) =>
        login(user)(logger.withConstContext(user.toStringMap))
      }
  )

  def register(registration: UserRegistration)(implicit logger: Logger[Task]) =
    infoSpan {
      for {
        _ <- logger.debugU(s"Registering ${registration.username}")
        (_, id) <- U.createUser(registration)
      } yield RegistrationResponse(id)
    }

  val registrationRoute = toRoutes(
    AccountEndpoints.registrationEndpoint
      .serverLogicPart(enrichLogger)
      .andThenRecoverErrors { case (logger, registration) =>
        register(registration)(
          logger.withConstContext(registration.toStringMap)
        )
      }
  )

  val logoutRoute = toRoutes(
    AccountEndpoints.logoutEndpoint
      .serverLogicRecoverErrors { case (ctx, details) =>
        authorize(ctx, details)(UserRole.User) { case (logger, authDetails) =>
          authService.logout(authDetails.user.id)(logger)
        }.leftWiden[Throwable]
      }
  )

  def activateAccount(token: FUUID)(implicit logger: Logger[Task]) =
    infoSpan { U.activateAccount(token) }

  val accountActivationRoute = toRoutes(
    AccountEndpoints.accountActivationRoute
      .serverLogicPart(enrichLogger)
      .andThenRecoverErrors { case (logger, token) =>
        activateAccount(token)(logger)
      }
  )

  val routes =
    loginRoute <+> registrationRoute <+> logoutRoute <+> accountActivationRoute
}
