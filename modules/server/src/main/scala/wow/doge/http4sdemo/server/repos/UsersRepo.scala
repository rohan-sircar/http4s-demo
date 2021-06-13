package wow.doge.http4sdemo.server.repos

import cats.effect.concurrent.Ref
import cats.syntax.all._
import eu.timepit.refined.auto._
import io.odin.Logger
import io.odin.meta.Position
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
  def put(nu: NewUser)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Refinements.UserId]
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

final class InMemoryUsersRepo private (
    lock: MLock,
    counter: Ref[Task, Refinements.UserId],
    store: Ref[Task, List[User]]
) extends UsersRepo {

  private def cake[E, A](
      io: IO[E, A]
  )(implicit logger: Logger[Task], position: Position) =
    infoSpan(lock.greenLight(io))

  def put(
      nu: NewUser
  )(implicit logger: Logger[Task]): IO[AppError2, Refinements.UserId] =
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
        num <- counter.get.hideErrors
        user2 = nu.into[User].withFieldConst(_.id, num).transform
        num <- counter.updateAndGet(_ :+ Refinements.UserId(1)).hideErrors
        _ <- store.update(user2 :: _).hideErrors
      } yield num
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

object InMemoryUsersRepo {
  def apply() = for {
    lock <- MLock()
    counter <- Ref
      .of[Task, Refinements.UserId](Refinements.UserId(1))
      .hideErrors
    store <- Ref.of[Task, List[User]](List.empty).hideErrors
    repo = new InMemoryUsersRepo(lock, counter, store)
  } yield repo
}

final class UsersRepoImpl(db: JdbcBackend.DatabaseDef, usersDbio: UsersDbio)
    extends UsersRepo {

  def put(
      nu: NewUser
  )(implicit logger: Logger[Task]): IO[AppError2, Refinements.UserId] =
    infoSpan {
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
    }

  def getById(
      userId: Refinements.UserId
  )(implicit logger: Logger[Task]): IO[AppError2, Option[User]] = infoSpan {
    for {
      _ <- logger.infoU("Getting user")
      res <- db.runIO(usersDbio.getUserById(userId).transactionally)
    } yield res
  }

  def getByName(
      userName: Refinements.Username
  )(implicit logger: Logger[Task]): IO[AppError2, Option[User]] = infoSpan {
    for {
      _ <- logger.infoU("Getting user")
      res <- db.runIO(usersDbio.getUserByName(userName).transactionally)
    } yield res
  }

  def remove(userId: Refinements.UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] = ???

}

final class UsersDbio {

  def insertUser(nu: NewUser) = Tables.Users
    .map(NewUser.fromUsersTableFn)
    .returning(Tables.Users.map(_.userId)) += nu

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
