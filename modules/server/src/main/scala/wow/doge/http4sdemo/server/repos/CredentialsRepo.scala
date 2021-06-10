package wow.doge.http4sdemo.server.repos

import cats.effect.concurrent.Ref
import cats.syntax.eq._
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.refinements.Refinements.UserId
import wow.doge.http4sdemo.server.auth.JwtToken
import wow.doge.http4sdemo.utils.MLock
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256

trait CredentialsRepo {
  def put(userId: UserId, jwt: JWTMac[HMACSHA256]): IO[AppError2, Unit]
  def get(userId: UserId): IO[AppError2, Option[JwtToken]]
  def remove(userId: UserId): IO[AppError2, Unit]
}

final class InMemoryCredentialsRepo private (
    lock: MLock,
    store: Ref[Task, Map[UserId, JwtToken]]
) extends CredentialsRepo {

  def put(userId: UserId, jwt: JWTMac[HMACSHA256]): IO[AppError2, Unit] =
    lock.greenLight {
      for {
        s <- store.get.hideErrors
        next <- s.get(userId) match {
          case Some(_) =>
            IO.raiseError(
              AppError2.EntityAlreadyExists(
                s"token for uid: $userId already exists"
              )
            )
          case None => IO.pure(s + (userId -> JwtToken(jwt)))
        }
        _ <- store.set(next).hideErrors
      } yield ()

    }

  def remove(userId: UserId): IO[AppError2, Unit] = lock.greenLight {
    for {
      s <- store.get.hideErrors
      _ <- s.get(userId) match {
        case Some(_) =>
          IO.raiseError(
            AppError2.EntityDoesNotExist(
              s"token for uid: $userId does not exist"
            )
          )
        case None => IO.unit
      }
      next = s - userId
      _ <- store.set(next).hideErrors
    } yield ()
  }

  def get(userId: UserId): IO[AppError2, Option[JwtToken]] =
    lock.greenLight { store.get.map(_.get(userId)).hideErrors }

}

object InMemoryCredentialsRepo {
  def apply() = for {
    lock <- MLock()
    store <- Ref.of[Task, Map[UserId, JwtToken]](Map.empty).hideErrors
    repo = new InMemoryCredentialsRepo(lock, store)
  } yield repo
}
