package wow.doge.http4sdemo.server.services

import cats.syntax.eq._
import eu.timepit.refined.auto._
import io.odin.Logger
import io.odin.syntax._
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import tsec.common.TSecError
import tsec.common.VerificationFailed
import tsec.common.Verified
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.endpoints.AuthDetails
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.UserIdentity
import wow.doge.http4sdemo.models.UserLogin
import wow.doge.http4sdemo.models.UserRegistration
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.server.auth._
import wow.doge.http4sdemo.server.implicits._
import wow.doge.http4sdemo.server.repos.CredentialsRepo
import wow.doge.http4sdemo.server.repos.UsersRepo

trait AuthService {
  def verify(authDetails: AuthDetails)(implicit
      logger: Logger[Task]
  ): IO[AppError2, VerifiedAuthDetails]

  def login(user: UserLogin)(implicit
      logger: Logger[Task]
  ): IO[AppError2, JWTMac[HMACSHA256]]

  def register(user: UserRegistration)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit]
}

final class AuthServiceImpl(C: CredentialsRepo, U: UsersRepo)(implicit
    key: JwtSigningKey
) extends AuthService {

  val bcryptHash: Task[PasswordHash[BCrypt]] = BCrypt.hashpw[Task]("hiThere")

  def verify(authDetails: AuthDetails)(implicit logger: Logger[Task]) = {
    for {
      decoded <- decode[Task](authDetails.bearerToken)
        .mapErrorPartialWith { case e: TSecError =>
          logger.errorU(s"Failed to decode auth token ${e.getMessage}") >>
            IO.raiseError(AppError2.AuthError("Failed to decode auth token"))
        }
      existingToken <- C.get(decoded.user.id)
      // ctx = Map("userId" -> decoded.user.id.inner.toString)
      logger <- IO.pure(
        logger.withConstContext(Map("userId" -> decoded.user.id.inner.toString))
      )
      _ <- existingToken match {
        case Some(value) =>
          if (value === decoded.jwt) logger.infoU("Auth successful")
          else
            logger.warnU(
              "Invalid auth token: token did not match with session token"
            ) >> IO.raiseError(AppError2.AuthError("Invalid token"))
        case None =>
          logger.warnU(
            "Invalid auth token: user does not have an existing token"
          ) >> IO.raiseError(AppError2.AuthError("Invalid token"))
      }
    } yield decoded
  }

  def login(
      userLogin: UserLogin
  )(implicit logger: Logger[Task]): IO[AppError2, JWTMac[HMACSHA256]] = {
    for {
      _ <- logger.infoU("Performing login")
      mbUser <- U.getByName(userLogin.username)
      user <- IO.fromOption(mbUser, AppError2.AuthError("Invalid password"))
      identity = user.transformInto[UserIdentity]
      status <- checkPasswordIO(userLogin, user)
      jwt <- status match {
        case VerificationFailed =>
          IO.raiseError(AppError2.AuthError("Invalid password"))
        case Verified => encode[Task](identity).hideErrors
      }
      _ <- C.put(user.id, jwt)
    } yield jwt
  }

  def register(
      user: UserRegistration
  )(implicit logger: Logger[Task]): IO[AppError2, Unit] = {
    for {
      _ <- logger.infoU("Registering user")
      hashed <- hashPasswordIO(user.password)
      nu = user
        .into[NewUser]
        .withFieldConst(_.password, hashed)
        .withFieldConst(_.role, UserRole.User)
        .transform
      _ <- U.put(nu)
    } yield ()
  }
}

class NoOpAuthService extends AuthService {
  def verify(authDetails: AuthDetails)(implicit
      logger: Logger[Task]
  ): IO[AppError2, VerifiedAuthDetails] = IO.terminate(new NotImplementedError)
  def login(
      user: UserLogin
  )(implicit logger: Logger[Task]): IO[AppError2, JWTMac[HMACSHA256]] =
    IO.terminate(new NotImplementedError)
  def register(
      user: UserRegistration
  )(implicit logger: Logger[Task]): IO[AppError2, Unit] =
    IO.terminate(new NotImplementedError)
}
