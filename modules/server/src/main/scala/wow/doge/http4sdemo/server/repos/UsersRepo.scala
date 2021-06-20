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
  def put(nu: NewUser)(implicit logger: Logger[Task]): IO[AppError2, UserId]
  def getById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Option[UserEntity]]
  def getByName(userName: Username)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Option[UserEntity]]
  def removeById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit]
  def updateRoleById(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit]
}

final class InMemoryUsersRepo private (
    lock: MLock,
    counter: Ref[Task, UserId],
    store: Ref[Task, List[UserEntity]]
) extends UsersRepo {

  private def cake[E, A](
      io: IO[E, A]
  )(implicit logger: Logger[Task], position: Position) =
    infoSpan(lock.greenLight(io))

  def put(nu: NewUser)(implicit logger: Logger[Task]): IO[AppError2, UserId] =
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
        user2 = nu.into[UserEntity].withFieldConst(_.id, num).transform
        num <- counter.updateAndGet(_ :+ UserId(1)).hideErrors
        _ <- store.update(user2 :: _).hideErrors
      } yield num
    }

  def getById(userId: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Option[UserEntity]] = cake {
    for {
      users <- store.get.hideErrors
      res = users.find(_.id === userId)
    } yield res
  }

  def getByName(userName: Username)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Option[UserEntity]] = cake {
    for {
      users <- store.get.hideErrors
      res = users.find(_.username === userName)
    } yield res
  }

  def removeById(userId: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] =
    cake {
      for {
        users <- store.get.hideErrors
        next = users.filterNot(_.id === userId)
        _ <- store.set(next).hideErrors
      } yield ()
    }

  def updateRoleById(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] = cake {
    for {
      users <- store.get.hideErrors
      next = users.foldLeft(List.empty[UserEntity]) { case (acc, next) =>
        if (next.id === id) next.copy(role = role) :: acc else next :: acc
      }
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
  )(implicit logger: Logger[Task]): IO[AppError2, UserId] =
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
      userId: UserId
  )(implicit logger: Logger[Task]): IO[AppError2, Option[UserEntity]] =
    infoSpan {
      for {
        _ <- logger.infoU("Getting user")
        res <- db.runIO(usersDbio.getUserById(userId).transactionally)
      } yield res
    }

  def getByName(
      userName: Username
  )(implicit logger: Logger[Task]): IO[AppError2, Option[UserEntity]] =
    infoSpan {
      for {
        _ <- logger.infoU("Getting user")
        res <- db.runIO(usersDbio.getUserByName(userName).transactionally)
      } yield res
    }

  def removeById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] = infoSpan {
    db.runIO(usersDbio.removeById(id).transactionally >> DBIO.unit)
  }

  def updateRoleById(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] = infoSpan {
    db.runIO(usersDbio.updateRoleById(id, role).transactionally >> DBIO.unit)
  }

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

}
