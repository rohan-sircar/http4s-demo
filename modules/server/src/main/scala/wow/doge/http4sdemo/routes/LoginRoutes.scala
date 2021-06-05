package wow.doge.http4sdemo.routes

import io.odin.Logger
import monix.bio.Task
import wow.doge.http4sdemo.endpoints.LoginEndpoints
import wow.doge.http4sdemo.endpoints.LoginResponse
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.UserLogin
import wow.doge.http4sdemo.server.auth.AuthService

final class LoginRoutes(A: AuthService)(val logger: Logger[Task])
    extends ServerInterpreter {

  def login(user: UserLogin)(implicit logger: Logger[Task]) = for {
    _ <- logger.debugU(s"Logging in $user")
    jwt <- A.login(user)
    res = LoginResponse(jwt.toEncodedString)
    _ <- logger.debugU(s"Response: $res")
  } yield res

  val loginRoute = toRoutes(
    LoginEndpoints.loginEndpoint.serverLogicPart(enrichLogger).andThen {
      case (logger, user) => login(user)(logger).attempt
    }
  )

  val routes = loginRoute
}
