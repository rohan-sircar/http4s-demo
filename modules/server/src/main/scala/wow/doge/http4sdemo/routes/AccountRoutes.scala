package wow.doge.http4sdemo.routes

import cats.syntax.all._
import io.odin.Logger
import io.odin.syntax._
import monix.bio.Task
import wow.doge.http4sdemo.endpoints.AccountEndpoints
import wow.doge.http4sdemo.endpoints.LoginResponse
import wow.doge.http4sdemo.endpoints.RegistrationResponse
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.UserLogin
import wow.doge.http4sdemo.models.UserRegistration
import wow.doge.http4sdemo.server.services.AuthService

final class AccountRoutes(A: AuthService)(val logger: Logger[Task])
    extends ServerInterpreter {

  def login(user: UserLogin)(implicit logger: Logger[Task]) = for {
    _ <- logger.debugU(s"Logging in")
    jwt <- A.login(user)
    res = LoginResponse(jwt.toEncodedString)
  } yield res

  val loginRoute = toRoutes(
    AccountEndpoints.loginEndpoint
      .serverLogicPart(enrichLogger)
      .andThenRecoverErrors { case (logger, user) =>
        login(user)(logger.withConstContext(user.toStringMap))
      }
  )

  def register(registration: UserRegistration)(implicit logger: Logger[Task]) =
    for {
      _ <- logger.debugU(s"Registering ${registration.username}")
      _ <- A.register(registration)
    } yield RegistrationResponse("Success!")

  val registrationRoute = toRoutes(
    AccountEndpoints.registrationEndpoint
      .serverLogicPart(enrichLogger)
      .andThenRecoverErrors { case (logger, registration) =>
        register(registration)(
          logger.withConstContext(registration.toStringMap)
        )
      }
  )

  val routes = loginRoute <+> registrationRoute
}
