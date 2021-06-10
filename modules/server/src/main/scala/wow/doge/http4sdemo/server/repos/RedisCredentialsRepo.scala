package wow.doge.http4sdemo.server.repos

import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import monix.bio.IO
import monix.bio.Task
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.refinements.Refinements.UserId
import wow.doge.http4sdemo.server.auth.JwtSigningKey
import wow.doge.http4sdemo.server.auth.JwtToken

final class RedisCredentialsRepo(redis: RedisCommands[Task, String, String])(
    implicit key: JwtSigningKey
) extends CredentialsRepo {

  private def key(uid: UserId) = {
    val prefix = "user-session"
    s"$prefix:$uid"
  }

//   private val tx = transactions.RedisTransaction(redis)

  def put(userId: UserId, jwt: JWTMac[HMACSHA256]): IO[AppError2, Unit] = {
    // val commands = redis
    //   .get(userId.toString)
    //   .flatTap {
    //     case Some(_) =>
    //       IO.raiseError(
    //         AppError2.EntityAlreadyExists(
    //           s"token for uid: $userId already exists"
    //         )
    //       )
    //     case None => IO.unit
    //   }
    //   .hideErrors ::
    //   redis.set(userId.toString, jwt.toString).hideErrors :: HNil

    // tx.filterExec(commands).mapErrorPartial { case e: AppError2 => e }.void
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

  def remove(userId: UserId): IO[AppError2, Unit] = {
    // val commands = redis
    //   .get(userId.toString)
    //   .flatTap {
    //     case Some(_) =>
    //       IO.raiseError(
    //         AppError2.EntityDoesNotExist(
    //           s"token for uid: $userId does not exist"
    //         )
    //       )
    //     case None => IO.unit
    //   }
    //   .hideErrors ::
    //   redis.del(userId.toString).hideErrors :: HNil

    // tx.filterExec(commands).mapErrorPartial { case e: AppError2 => e }.void
    for {
      token <- redis.get(key(userId)).hideErrors
      _ <- token match {
        case Some(_) =>
          IO.raiseError(
            AppError2.EntityDoesNotExist(
              s"token for uid: $userId does not exist"
            )
          )
        case None => IO.unit
      }
      _ <- redis.del(key(userId)).hideErrors
    } yield ()
  }

  def get(userId: UserId): IO[AppError2, Option[JwtToken]] = {
    // val commands = redis.get(userId.toString) :: HNil
    // for {
    //   txRes <- tx
    //     .filterExec(commands)
    //     .mapErrorPartial { case e: AppError2 => e }
    //   t <- txRes match {
    //     case res1 ~: HNil => res1.traverse(JwtToken.fromTokenStr)
    //   }
    // } yield t
    for {
      u <- redis.get(key(userId)).hideErrors
      t <- u.traverse(JwtToken.fromTokenStr)
    } yield t
  }

}

// object RedisCredentialsRepo {
//   def apply() = for {
//     lock <- MLock()
//     store <- Ref.of[Task, Map[UserId, JwtToken]](Map.empty).hideErrors
//     repo = new RedisCredentialsRepo(lock, store)
//   } yield repo
// }
