package wow.doge.http4sdemo.server.repos

import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import slick.jdbc.JdbcBackend
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.User
import wow.doge.http4sdemo.refinements.Refinements
import wow.doge.http4sdemo.server.ExtendedPgProfile.api._
import wow.doge.http4sdemo.server.ExtendedPgProfile.mapping._
import wow.doge.http4sdemo.server.implicits._
import wow.doge.http4sdemo.slickcodegen.Tables

final class UsersRepo(db: JdbcBackend.DatabaseDef, usersDbio: UsersDbio) {
  //TODO add validation
  def put(nu: NewUser)(implicit logger: Logger[Task]): IO[AppError2, Int] =
    for {
      _ <- logger.infoU("Putting user")
      res <- db
        .runIO(usersDbio.insertUser(nu).transactionally)
    } yield res

  def getById(
      userId: Refinements.UserId
  )(implicit logger: Logger[Task]): IO[AppError2, Option[User]] = {
    for {
      _ <- logger.infoU("Getting user")
      res <- db
        .runIO(usersDbio.getUserById(userId).transactionally)
    } yield res
  }

  def getByName(
      userName: Refinements.Username
  )(implicit logger: Logger[Task]): IO[AppError2, Option[User]] = {
    for {
      _ <- logger.infoU("Getting user")
      res <- db
        .runIO(usersDbio.getUserByName(userName).transactionally)
    } yield res
  }

//   def remove(userId: UserId): IO[AppError2, Unit]
}

final class UsersDbio {

  def insertUser(nu: NewUser) = Tables.Users.map(NewUser.fromUsersTableFn) += nu

  def getUserById(id: Refinements.UserId) =
    Tables.Users
      .filter(_.userId === id)
      .map(User.fromUsersTableFn)
      .result
      .headOption

  def getUserByName(userName: Refinements.Username) =
    Tables.Users
      .filter(_.userName === userName)
      .map(User.fromUsersTableFn)
      .result
      .headOption

}
