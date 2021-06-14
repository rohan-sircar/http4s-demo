package wow.doge.http4sdemo.server.services

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import cats.effect.Resource
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
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.endpoints.AuthDetails
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.UserIdentity
import wow.doge.http4sdemo.models.UserLogin
import wow.doge.http4sdemo.models.UserRegistration
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements
import wow.doge.http4sdemo.server.auth._
import wow.doge.http4sdemo.server.repos.CredentialsRepo
import wow.doge.http4sdemo.server.repos.InMemoryCredentialsRepo
import wow.doge.http4sdemo.server.repos.InMemoryUsersRepo
import wow.doge.http4sdemo.server.repos.UsersRepo
import wow.doge.http4sdemo.utils.infoSpan

trait AuthService {
  def verify(authDetails: AuthDetails)(implicit
      logger: Logger[Task]
  ): IO[AppError2, VerifiedAuthDetails]

  def login(user: UserLogin)(implicit
      logger: Logger[Task]
  ): IO[AppError2, JWTMac[HMACSHA256]]

  def register(user: UserRegistration)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Refinements.UserId]
}

final class AuthServiceImpl(
    C: CredentialsRepo,
    U: UsersRepo,
    tokenTimeout: FiniteDuration
)(implicit key: JwtSigningKey)
    extends AuthService {

  def verify(authDetails: AuthDetails)(implicit logger: Logger[Task]) =
    infoSpan {
      for {
        decoded <- decode[Task](authDetails.bearerToken)
          .mapErrorPartialWith { case e: TSecError =>
            logger.errorU(s"Failed to decode auth token ${e.getMessage}") >>
              IO.raiseError(AppError2.AuthError("Failed to decode auth token"))
          }
        existingToken <- C.get(decoded.user.id)
        // ctx = Map("userId" -> decoded.user.id.inner.toString)
        logger <- IO.pure(
          logger.withConstContext(
            Map("userId" -> decoded.user.id.inner.toString)
          )
        )
        _ <- existingToken match {
          case Some(value) =>
            if (value === JwtToken(decoded.jwt)) logger.infoU("Auth successful")
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
  )(implicit logger: Logger[Task]): IO[AppError2, JWTMac[HMACSHA256]] =
    infoSpan {
      for {
        // _ <- logger.infoU("Performing login")
        mbUser <- U.getByName(userLogin.username)
        user <- IO.fromOption(mbUser, AppError2.AuthError("Invalid password"))
        identity = user.transformInto[UserIdentity]
        status <- checkPasswordIO(userLogin, user)
        jwt <- status match {
          case VerificationFailed =>
            IO.raiseError(AppError2.AuthError("Invalid password"))
          case Verified =>
            encode[Task](identity, tokenTimeout).hideErrors
        }
        _ <- C.put(user.id, jwt)
      } yield jwt
    }

  def register(
      user: UserRegistration
  )(implicit logger: Logger[Task]): IO[AppError2, Refinements.UserId] =
    infoSpan {
      for {
        _ <- logger.infoU("Registering user")
        hashed <- hashPasswordIO(user.password)
        nu = user
          .into[NewUser]
          .withFieldConst(_.password, hashed)
          .withFieldConst(_.role, UserRole.User)
          .transform
        id <- U.put(nu)
      } yield id
    }
}

object AuthServiceImpl {
  def inMemory(
      tokenTimeout: FiniteDuration = 10.seconds,
      interval: FiniteDuration = 10.seconds
  )(implicit
      key: JwtSigningKey,
      logger: Logger[Task]
  ) =
    for {
      crepo <- InMemoryCredentialsRepo(tokenTimeout, interval)(logger)
      urepo <- Resource.eval(InMemoryUsersRepo()),
    } yield new AuthServiceImpl(crepo, urepo, tokenTimeout)

  def inMemoryWithUser(
      nu: NewUser,
      tokenTimeout: FiniteDuration = 10.seconds,
      interval: FiniteDuration = 10.seconds
  )(implicit key: JwtSigningKey, logger: Logger[Task]) = {
    for {
      urepo <- Resource.eval(InMemoryUsersRepo())
      id <- Resource.eval(urepo.put(nu))
      identity = nu.into[UserIdentity].withFieldConst(_.id, id).transform
      jwt <- Resource.eval(encode[Task](identity, tokenTimeout).hideErrors)
      crepo <- InMemoryCredentialsRepo(tokenTimeout, interval)(logger)
      _ <- Resource.eval(crepo.put(id, jwt))
      impl = new AuthServiceImpl(crepo, urepo, tokenTimeout)
      token = JwtToken(jwt)
    } yield (id, token, impl)
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
  )(implicit logger: Logger[Task]): IO[AppError2, Refinements.UserId] =
    IO.terminate(new NotImplementedError)
}
