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

  def logout(id: Refinements.UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit]

}

sealed class AuthServiceImpl(
    C: CredentialsRepo,
    U: UsersRepo,
    tokenTimeout: FiniteDuration
)(implicit key: JwtSigningKey)
    extends AuthService {

  def logout(id: Refinements.UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] = infoSpan {
    C.remove(id)
  }

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

}

object AuthServiceImpl {
  def inMemory(
      usersRepo: Option[UsersRepo] = None,
      tokenTimeout: FiniteDuration = 10.seconds,
      interval: FiniteDuration = 10.seconds
  )(implicit key: JwtSigningKey, logger: Logger[Task]) =
    for {
      crepo <- InMemoryCredentialsRepo(tokenTimeout, interval)(logger)
      urepo <- usersRepo match {
        case Some(value) => Resource.pure[Task, UsersRepo](value)
        case None        => Resource.eval(InMemoryUsersRepo())
      }
    } yield new AuthServiceImpl(crepo, urepo, tokenTimeout)
}

final class TestAuthService(
    C: CredentialsRepo,
    U: UsersRepo,
    tokenTimeout: FiniteDuration
)(implicit key: JwtSigningKey)
    extends AuthServiceImpl(C, U, tokenTimeout) {
  def createAuthedUser(nu: NewUser)(implicit logger: Logger[Task]) = {
    for {
      id <- U.put(nu)
      identity = nu.into[UserIdentity].withFieldConst(_.id, id).transform
      jwt <- encode[Task](identity, tokenTimeout).hideErrors
      _ <- C.put(id, jwt)
      token = JwtToken(jwt)
    } yield (id, token)
  }
}

object TestAuthService {
  def apply(
      usersRepo: Option[UsersRepo] = None,
      tokenTimeout: FiniteDuration = 10.seconds
      // interval: FiniteDuration = 10.seconds
  )(implicit key: JwtSigningKey) =
    for {
      crepo <- InMemoryCredentialsRepo.withoutInvalidator()
      urepo <- usersRepo match {
        case Some(value) => IO.pure(value)
        case None        => InMemoryUsersRepo()
      }
    } yield new TestAuthService(crepo, urepo, tokenTimeout)
}

class NoOpAuthService extends AuthService {
  def verify(authDetails: AuthDetails)(implicit
      logger: Logger[Task]
  ): IO[AppError2, VerifiedAuthDetails] = IO.terminate(new NotImplementedError)
  def login(
      user: UserLogin
  )(implicit logger: Logger[Task]): IO[AppError2, JWTMac[HMACSHA256]] =
    IO.terminate(new NotImplementedError)

  def logout(id: Refinements.UserId)(implicit
      logger: Logger[Task]
  ): IO[AppError2, Unit] = IO.terminate(new NotImplementedError)
}
