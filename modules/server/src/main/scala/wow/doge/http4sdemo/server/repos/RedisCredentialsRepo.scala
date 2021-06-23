package wow.doge.http4sdemo.server.repos

import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.refinements.Refinements.UserId
import wow.doge.http4sdemo.server.auth.JwtSigningKey
import wow.doge.http4sdemo.server.auth.JwtToken

final class RedisCredentialsRepo(redis: RedisCommands[Task, String, String])(
    implicit signingKey: JwtSigningKey
) extends CredentialsRepo {

  private def key(uid: UserId) = s"users:$uid:session.token"

  def put(userId: UserId, jwt: JWTMac[HMACSHA256])(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] = {
    for {
      token <- redis.get(key(userId)).hideErrors
      _ <- token match {
        case Some(_) =>
          IO.raiseError(
            AppError2.EntityAlreadyExists(
              s"token for uid: $userId already exists"
            )
          )
        case None => IO.unit
      }
      _ <- redis.set(key(userId), JwtToken(jwt).inner).hideErrors
    } yield ()
  }

  def remove(
      userId: UserId
  )(implicit logger: Logger[Task]): IO[AppError2, Unit] = {
    for {
      token <- redis.get(key(userId)).hideErrors
      _ <- token match {
        case Some(_) => IO.unit
        case None =>
          IO.raiseError(
            AppError2.EntityDoesNotExist(
              s"token for uid: $userId does not exist"
            )
          )
      }
      _ <- redis.del(key(userId)).hideErrors
    } yield ()
  }

  def get(
      userId: UserId
  )(implicit logger: Logger[Task]): IO[AppError2, Option[JwtToken]] = {
    for {
      u <- redis.get(key(userId)).hideErrors
      t <- u.traverse(JwtToken.fromTokenStr)
    } yield t
  }

}
