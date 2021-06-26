package wow.doge.http4sdemo.server.services

// import eu.timepit.refined.auto._
import io.odin.Logger
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.UserEntity
import wow.doge.http4sdemo.models.UserRegistration
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.auth._
import wow.doge.http4sdemo.server.repos.UsersRepo
import wow.doge.http4sdemo.utils.infoSpan

trait UserService {
  def getUserById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[UserEntity]]
  def createUser(user: UserRegistration)(implicit
      logger: Logger[Task]
  ): IO[AppError, UserId]
  def changeRole(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit]
}

final class UserServiceImpl(U: UsersRepo) extends UserService {

  def getUserById(id: UserId)(implicit logger: Logger[Task]) = infoSpan {
    U.getById(id)
  }

  def createUser(
      user: UserRegistration
  )(implicit logger: Logger[Task]): IO[AppError, UserId] =
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
  ): IO[AppError, Unit] =
    infoSpan { U.updateRoleById(id, role) }
}

class NoopUserService extends UserService {

  def getUserById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[UserEntity]] = IO.terminate(new NotImplementedError)

  def createUser(user: UserRegistration)(implicit
      logger: Logger[Task]
  ): IO[AppError, UserId] = IO.terminate(new NotImplementedError)

  def changeRole(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = IO.terminate(new NotImplementedError)

}
