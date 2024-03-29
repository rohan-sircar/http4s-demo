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
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.UserEntity
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.ExtendedPgProfile.api._
import wow.doge.http4sdemo.server.ExtendedPgProfile.mapping._
import wow.doge.http4sdemo.server.implicits._
import wow.doge.http4sdemo.slickcodegen.Tables
import wow.doge.http4sdemo.utils.MLock
import wow.doge.http4sdemo.utils.infoSpan

trait UsersRepo {
  def put(nu: NewUser)(implicit logger: Logger[Task]): IO[AppError, UserId]
  def getById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[UserEntity]]
  def getByName(userName: Username)(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[UserEntity]]
  def activateById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit]
  def removeById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit]
  def updateRoleById(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit]
}

final class InMemoryUsersRepo private (
    lock: MLock,
    counter: Ref[Task, UserId],
    store: Ref[Task, List[UserEntity]]
) extends UsersRepo {

  private def cake[E, A](
      io: IO[E, A]
  )(implicit logger: Logger[Task], position: Position) =
    infoSpan(io)

  def put(nu: NewUser)(implicit logger: Logger[Task]): IO[AppError, UserId] =
    cake {
      for {
        users <- store.get.hideErrors
        user = users.find(_.username === nu.username)
        _ <- user match {
          case Some(value) =>
            IO.raiseError(
              AppError.EntityAlreadyExists(
                s"user with username: ${nu.username} already exists"
              )
            )
          case None => IO.unit
        }
        num <- counter.get.hideErrors
        _ <- logger.traceU(s"Generated id = $num")
        user2 = nu
          .into[UserEntity]
          .withFieldConst(_.id, num)
          .withFieldConst(_.activeStatus, false)
          .transform
        _ <- counter.updateAndGet(_ :+ UserId(1)).hideErrors

        _ <- store.update(user2 :: _).hideErrors
      } yield num
    }

  def getById(userId: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[UserEntity]] = cake {
    for {
      users <- store.get.hideErrors
      res = users.find(_.id === userId)
    } yield res
  }

  def getByName(userName: Username)(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[UserEntity]] = cake {
    for {
      users <- store.get.hideErrors
      res = users.find(_.username === userName)
    } yield res
  }

  def removeById(userId: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] =
    cake {
      for {
        users <- store.get.hideErrors
        next = users.filterNot(_.id === userId)
        _ <- store.set(next).hideErrors
      } yield ()
    }

  def updateRoleById(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = cake {
    for {
      users <- store.get.hideErrors
      next = users.foldLeft(List.empty[UserEntity]) { case (acc, next) =>
        if (next.id === id) next.copy(role = role) :: acc else next :: acc
      }
      _ <- store.set(next).hideErrors
    } yield ()
  }

  def activateById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = cake {
    for {
      users <- store.get.hideErrors
      next = users.foldLeft(List.empty[UserEntity]) { case (acc, next) =>
        if (next.id === id) next.copy(activeStatus = true) :: acc
        else next :: acc
      }
      _ <- logger.traceU(s"User id = $id")
      _ <- logger.traceU(s"Users = $next")
      _ <- store.set(next).hideErrors
    } yield ()
  }
}

object InMemoryUsersRepo {
  def apply() = for {
    lock <- MLock()
    counter <- Ref
      .of[Task, UserId](UserId(1))
      .hideErrors
    store <- Ref.of[Task, List[UserEntity]](List.empty).hideErrors
    repo = new InMemoryUsersRepo(lock, counter, store)
  } yield repo
}

final class UsersRepoImpl(db: JdbcBackend.DatabaseDef, usersDbio: UsersDbio)
    extends UsersRepo {

  def put(
      nu: NewUser
  )(implicit logger: Logger[Task]): IO[AppError, UserId] =
    infoSpan {
      for {
        _ <- logger.infoU("Putting user")
        action <- UIO.deferAction(implicit s =>
          UIO(for {
            u <- usersDbio.getUserByName(nu.username)
            _ <- u match {
              case Some(_) =>
                DBIO.failed(
                  AppError.EntityAlreadyExists(
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
      userId: UserId
  )(implicit logger: Logger[Task]): IO[AppError, Option[UserEntity]] =
    infoSpan {
      for {
        _ <- logger.infoU("Getting user")
        res <- db.runIO(usersDbio.getUserById(userId).transactionally)
      } yield res
    }

  def getByName(
      userName: Username
  )(implicit logger: Logger[Task]): IO[AppError, Option[UserEntity]] =
    infoSpan {
      for {
        _ <- logger.infoU("Getting user")
        res <- db.runIO(usersDbio.getUserByName(userName).transactionally)
      } yield res
    }

  def removeById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = infoSpan {
    db.runIO(usersDbio.removeById(id).transactionally >> DBIO.unit)
  }

  def updateRoleById(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = infoSpan {
    db.runIO(usersDbio.updateRoleById(id, role).transactionally >> DBIO.unit)
  }

  def activateById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = db.runIO(usersDbio.activateById(id)).void
}

final class UsersDbio {

  def insertUser(nu: NewUser) = Tables.Users
    .map(NewUser.fromUsersTableFn)
    .returning(Tables.Users.map(_.userId)) += nu

  def getUserById(id: UserId) =
    Tables.Users
      .filter(_.userId === id)
      .map(UserEntity.fromUsersTableFn)
      .result
      .headOption

  def getUserByName(userName: Username) =
    Tables.Users
      .filter(_.userName === userName)
      .map(UserEntity.fromUsersTableFn)
      .result
      .headOption

  def removeById(id: UserId) = Tables.Users.filter(_.userId === id).delete

  def updateRoleById(id: UserId, role: UserRole) =
    Tables.Users.filter(_.userId === id).map(_.userRole).update(role)

  def activateById(id: UserId) =
    Tables.Users.filter(_.userId === id).map(_.activeStatus).update(true)

}
