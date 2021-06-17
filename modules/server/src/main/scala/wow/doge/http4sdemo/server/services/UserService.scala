package wow.doge.http4sdemo.server.services

// import eu.timepit.refined.auto._
import io.odin.Logger
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.UserRegistration
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.auth._
import wow.doge.http4sdemo.server.repos.UsersRepo
import wow.doge.http4sdemo.utils.infoSpan

final class UserService(U: UsersRepo) {
  def createUser(
      user: UserRegistration
  )(implicit logger: Logger[Task]): IO[AppError2, UserId] =
    infoSpan {
      for {
        hashed <- hashPasswordIO(user.password)
        nu = user
          .into[NewUser]
          .withFieldConst(_.password, hashed)
          .withFieldConst(_.role, UserRole.User)
          .transform
        id <- U.put(nu)
      } yield id
    }

  def changeRole(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] =
    infoSpan { U.updateRoleById(id, role) }
}
