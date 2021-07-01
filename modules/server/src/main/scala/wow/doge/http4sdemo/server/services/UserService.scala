package wow.doge.http4sdemo.server.services

import scala.concurrent.duration._

import cats.syntax.all._
import io.chrisdavenport.fuuid.FUUID
import io.odin.Logger
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.AppError.CouldNotConnectError
import wow.doge.http4sdemo.AppError.MailClientError
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.User
import wow.doge.http4sdemo.models.UserEntity
import wow.doge.http4sdemo.models.UserRegistration
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.auth._
import wow.doge.http4sdemo.server.repos.AccountActivationTokensRepo
import wow.doge.http4sdemo.server.repos.UsersRepo
import wow.doge.http4sdemo.server.utils.Backoff
import wow.doge.http4sdemo.server.utils.MailClient
import wow.doge.http4sdemo.utils.infoSpan
trait UserService {
  def getUserById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[UserEntity]]
  def createUser(user: UserRegistration)(implicit
      logger: Logger[Task]
  ): IO[AppError, (FUUID, UserId)]
  def activateAccount(token: FUUID)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit]
  def changeRole(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit]
}

final class UserServiceImpl(
    U: UsersRepo,
    M: MailClient,
    aatr: AccountActivationTokensRepo
) extends UserService {

  def getUserById(id: UserId)(implicit logger: Logger[Task]) = infoSpan {
    U.getById(id)
  }

  def createUser(
      user: UserRegistration
  )(implicit logger: Logger[Task]): IO[AppError, (FUUID, UserId)] =
    infoSpan {
      for {
        hashed <- hashPasswordIO(user.password)
        nu = user
          .into[NewUser]
          .withFieldConst(_.password, hashed)
          .withFieldConst(_.role, UserRole.User)
          .transform
        token <- FUUID.randomFUUID[Task].hideErrors
        _ <- logger.infoU(s"Created verification token ${token.show}")
        mail = {
          import emil.builder._
          import emil.markdown._
          MailBuilder.build[Task](
            From(M.smtpConfig.user),
            To(user.email.inner.value),
            Subject("Please verify your account at http4s-demo"),
            MarkdownBody(
              s"""|# Hello!
                  |
                  |Thank you for registering.
                  |
                  |Click this link to activate your account - [link](/api/activate-account/${token.show}).""".stripMargin
            )
          )
        }
        id <- U.put(nu)
        user = nu
          .into[User]
          .withFieldConst(_.id, id)
          .withFieldConst(_.activeStatus, false)
          .transform
        _ <- aatr.put(token, user)
        _ <- M
          .send(mail)
          // Restarts for a maximum of 3 times, with an initial delay of 1 second,
          // a delay that keeps being multiplied by 2
          .onErrorRestartLoop(Backoff(3, 1.second)) { (err, state, retry) =>
            err match {
              case MailClientError(CouldNotConnectError(message)) =>
                val Backoff(maxRetries, delay) = state
                if (maxRetries > 0)
                  logger.warnU(
                    s"Failed to send verification mail. Retrying after ${delay * 2} seconds - [${(3 - maxRetries) + 1}]"
                  ) >> retry(Backoff(maxRetries - 1, delay * 2))
                    .delayExecution(delay)
                else
                  // No retries left, rethrow the error
                  IO.raiseError(err)
              case MailClientError(_) =>
                IO.raiseError(err)
            }
          }
          .tapError(err =>
            logger.errorU(
              "Failed to send verification mail, removing user"
            ) >>
              U.removeById(id) >> aatr.remove(token) >> (err match {
                case MailClientError(CouldNotConnectError(_)) =>
                  IO.terminate(err)
                case _ => IO.unit
              })
          )

      } yield token -> id
    }

  def activateAccount(token: FUUID)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = infoSpan {
    for {
      user <- IO.fromOptionEval(
        aatr.get(token),
        AppError.BadInput("Token does not exist")
      )
      _ <- U.activateById(user.id)
      _ <- aatr.remove(token)
    } yield ()
  }

  def changeRole(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] =
    infoSpan { U.updateRoleById(id, role) }
}

class NoopUserService extends UserService {

  def getUserById(id: UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError, Option[UserEntity]] = IO.terminate(new NotImplementedError)

  def createUser(user: UserRegistration)(implicit
      logger: Logger[Task]
  ): IO[AppError, (FUUID, UserId)] = IO.terminate(new NotImplementedError)

  def changeRole(id: UserId, role: UserRole)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = IO.terminate(new NotImplementedError)

  def activateAccount(token: FUUID)(implicit
      logger: Logger[Task]
  ): IO[AppError, Unit] = IO.terminate(new NotImplementedError)

}
