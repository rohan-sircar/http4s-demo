package wow.doge.http4sdemo.server.repos

import java.time.Instant

import scala.concurrent.duration._

import cats.effect.Resource
import cats.effect.concurrent.Ref
import io.odin.Logger
import io.odin.meta.Position
import jp.ne.opt.chronoscala.NamespacedImports._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.refinements.Refinements.UserId
import wow.doge.http4sdemo.server.auth.JwtToken
import wow.doge.http4sdemo.utils.MLock
import wow.doge.http4sdemo.utils.infoSpan

trait CredentialsRepo {
  def put(userId: UserId, jwt: JWTMac[HMACSHA256])(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit]
  def get(userId: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Option[JwtToken]]
  def remove(userId: UserId)(implicit logger: Logger[Task]): IO[AppError2, Unit]
}

final class InMemoryCredentialsRepo private (
    lock: MLock,
    store: Ref[Task, Map[UserId, InMemoryCredentialsRepo.Node]],
    tokenTimeout: FiniteDuration,
    interval: FiniteDuration
) extends CredentialsRepo {
  import InMemoryCredentialsRepo._

  private def cake[E, A](
      io: IO[E, A]
  )(implicit logger: Logger[Task], position: Position) =
    // infoSpan(lock.greenLight(io))
    infoSpan(io)

  def put(userId: UserId, jwt: JWTMac[HMACSHA256])(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] =
    cake {
      for {
        s <- store.get.hideErrors
        now <- UIO(Instant.now)
        next <- s.get(userId) match {
          case Some(_) =>
            IO.raiseError(
              AppError2.EntityAlreadyExists(
                s"token for uid: $userId already exists"
              )
            )
          case None => IO.pure(s + (userId -> Node(JwtToken(jwt), now)))
        }
        _ <- store.set(next).hideErrors
      } yield ()

    }

  def remove(
      userId: UserId
  )(implicit logger: Logger[Task]): IO[AppError2, Unit] = cake {
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

  def get(userId: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Option[JwtToken]] =
    cake { store.get.map(_.get(userId).map(_.token)).hideErrors }

  private def tokenInvalidator(implicit logger: Logger[Task]) = { // ()
    // lock.greenLight {
    Observable
      .interval(10.seconds)
      .mapEval { _ =>
        val io = for {
          now <- UIO(Instant.now())
          _ <- logger.debug("Running token invalidator")
          // _ <- lock.greenLight()
          _ <- store.update(_.filterNot { case k -> v =>
            v.createdAt + 10.cs.seconds < now
          })
        } yield ()
        io.toTask
      }
  }
}

object InMemoryCredentialsRepo {

  final case class Node(token: JwtToken, createdAt: Instant)

  def apply(tokenTimeout: FiniteDuration, interval: FiniteDuration)(implicit
      logger: Logger[Task]
  ) = for {
    lock <- Resource.eval(MLock())
    store <- Resource.eval(
      Ref.of[Task, Map[UserId, Node]](Map.empty).hideErrors
    )
    repo = new InMemoryCredentialsRepo(lock, store, tokenTimeout, interval)
    _ <- Resource.make(repo.tokenInvalidator.completedL.toIO.start)(_.cancel)
  } yield repo
}
