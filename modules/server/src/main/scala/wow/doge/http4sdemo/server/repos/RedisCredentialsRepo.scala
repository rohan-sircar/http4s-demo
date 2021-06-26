package wow.doge.http4sdemo.server.repos

import scala.concurrent.duration.FiniteDuration

import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.refinements.Refinements.UserId
import wow.doge.http4sdemo.server.auth.JwtSigningKey
import wow.doge.http4sdemo.server.auth.JwtToken

final class RedisCredentialsRepo(
    redis: RedisCommands[Task, String, String],
    tokenTimeout: FiniteDuration
)(implicit
    signingKey: JwtSigningKey
) extends CredentialsRepo {

  private def key(uid: UserId) = s"users:$uid:session.token"

  def put(userId: UserId, jwt: JWTMac[HMACSHA256])(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = {
    for {
      token <- redis.get(key(userId)).hideErrors
      _ <- token match {
        case Some(_) =>
          IO.raiseError(
            AppError.EntityAlreadyExists(
              s"token for uid: $userId already exists"
            )
          )
        case None => IO.unit
      }
      _ <- redis
        .setEx(key(userId), JwtToken(jwt).inner, tokenTimeout)
        .hideErrors
    } yield ()
  }

  def remove(
      userId: UserId
  )(implicit logger: Logger[Task]): IO[AppError, Unit] = {
    for {
      token <- redis.get(key(userId)).hideErrors
      _ <- token match {
        case Some(_) => IO.unit
        case None =>
          IO.raiseError(
            AppError.EntityDoesNotExist(
              s"token for uid: $userId does not exist"
            )
          )
      }
      _ <- redis.del(key(userId)).hideErrors
    } yield ()
  }

  def get(
      userId: UserId
  )(implicit logger: Logger[Task]): IO[AppError, Option[JwtToken]] = {
    for {
      u <- redis.get(key(userId)).hideErrors
      t <- u.traverse(JwtToken.fromTokenStr)
    } yield t
  }

}
