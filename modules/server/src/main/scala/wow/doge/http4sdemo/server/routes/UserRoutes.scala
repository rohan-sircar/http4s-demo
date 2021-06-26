package wow.doge.http4sdemo.server.routes

import cats.syntax.all._
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import org.http4s.HttpRoutes
import wow.doge.http4sdemo.endpoints.UserEndpoints
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.server.services.AuthService
import wow.doge.http4sdemo.server.services.UserService

final class UserRoutes(
    U: UserService,
    val authService: AuthService
)(val logger: Logger[Task])
    extends AuthedServerInterpreter {
  val getUserRoute: HttpRoutes[Task] = toRoutes(
    UserEndpoints.getUserEndpoint
      .serverLogicRecoverErrors { case (ctx, details) =>
        authorize(ctx, details)(UserRole.User) { case (logger, authDetails) =>
          IO.pure(authDetails.user)
        }.leftWiden[Throwable]
      }
  )

  val routes = getUserRoute
}
