package wow.doge.http4sdemo.server.repos

import cats.effect.concurrent.Ref
import cats.syntax.all._
import eu.timepit.refined.auto._
import io.odin.Logger
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
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
import wow.doge.http4sdemo.utils.MLock
import wow.doge.http4sdemo.utils.infoSpan

trait UsersRepo {
  def put(nu: NewUser)(implicit logger: Logger[Task]): IO[AppError2, Int]
  def getById(
      userId: Refinements.UserId
  )(implicit logger: Logger[Task]): IO[AppError2, Option[User]]
  def getByName(
      userName: Refinements.Username
  )(implicit logger: Logger[Task]): IO[AppError2, Option[User]]
  def remove(userId: Refinements.UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit]
}

final class InMemoryUsersRepo(
    lock: MLock,
    counter: Ref[Task, Refinements.UserId],
    store: Ref[Task, List[User]]
) extends UsersRepo {

  private def cake[E, A](io: IO[E, A])(implicit logger: Logger[Task]) =
    infoSpan(lock.greenLight(io))

  def put(nu: NewUser)(implicit logger: Logger[Task]): IO[AppError2, Int] =
    cake {
      for {
        users <- store.get.hideErrors
        user = users.find(_.username === nu.username)
        _ <- user match {
          case Some(value) =>
            IO.raiseError(
              AppError2.EntityAlreadyExists(
                s"user with username: ${nu.username} already exists"
              )
            )
          case None => IO.unit
        }
        num <- counter.updateAndGet(_ :+ Refinements.UserId(1)).hideErrors
        user2 = nu.into[User].withFieldConst(_.id, num).transform
        _ <- store.update(user2 :: _).hideErrors
      } yield num.inner.value
    }

  def getById(userId: Refinements.UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Option[User]] = cake {
    for {
      users <- store.get.hideErrors
      res = users.find(_.id === userId)
    } yield res
  }

  def getByName(userName: Refinements.Username)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Option[User]] = cake {
    for {
      users <- store.get.hideErrors
      res = users.find(_.username === userName)
    } yield res
  }

  def remove(userId: Refinements.UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] =
    cake {
      for {
        users <- store.get.hideErrors
        next = users.filterNot(_.id === userId)
        _ <- store.set(next).hideErrors
      } yield ()
    }

}

final class UsersRepoImpl(db: JdbcBackend.DatabaseDef, usersDbio: UsersDbio)
    extends UsersRepo {

  //TODO add validation
  def put(nu: NewUser)(implicit logger: Logger[Task]): IO[AppError2, Int] =
    for {
      _ <- logger.infoU("Putting user")
      action <- UIO.deferAction(implicit s =>
        UIO(for {
          u <- usersDbio.getUserByName(nu.username)
          _ <- u match {
            case Some(_) =>
              DBIO.failed(
                AppError2.EntityAlreadyExists(
                  s"user with username: ${nu.username} already exists"
                )
              )
            case None => DBIO.unit
          }
          n <- usersDbio.insertUser(nu)
        } yield n)
      )
      res <- db.runIO(action.transactionally)
    } yield res

  def getById(
      userId: Refinements.UserId
  )(implicit logger: Logger[Task]): IO[AppError2, Option[User]] = {
    for {
      _ <- logger.infoU("Getting user")
      res <- db.runIO(usersDbio.getUserById(userId).transactionally)
    } yield res
  }

  def getByName(
      userName: Refinements.Username
  )(implicit logger: Logger[Task]): IO[AppError2, Option[User]] = {
    for {
      _ <- logger.infoU("Getting user")
      res <- db.runIO(usersDbio.getUserByName(userName).transactionally)
    } yield res
  }

  def remove(userId: Refinements.UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] = ???

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
