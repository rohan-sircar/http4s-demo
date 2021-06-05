package wow.doge.http4sdemo.server.auth

import cats.syntax.eq._
import eu.timepit.refined.auto._
import io.odin.Logger
import io.scalaland.chimney.dsl._
import monix.bio.IO
import monix.bio.Task
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.endpoints.AuthDetails
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.User
import wow.doge.http4sdemo.models.UserLogin
import wow.doge.http4sdemo.models.UserRole
import wow.doge.http4sdemo.refinements.Refinements
import wow.doge.http4sdemo.server.implicits._
import wow.doge.http4sdemo.server.repos.CredentialsRepo

final class AuthService(C: CredentialsRepo)(implicit key: JwtSigningKey) {
  def parseDetails(authDetails: AuthDetails)(implicit logger: Logger[Task]) = {
    for {
      parsedDetails <- decode[Task](authDetails.bearerToken).hideErrors
      token <- C.get(parsedDetails.user.id)
      _ <- token match {
        case Some(value) =>
          if (value === parsedDetails.jwt)
            logger.debugU("Auth successful") >>
              IO.unit
          else
            logger.warnU(
              "Invalid auth token: token did not match with session token"
            ) >>
              IO.raiseError(AppError2.AuthError("Invalid token"))
        case None =>
          logger.warnU(
            "Invalid auth token: user does not have an existing token"
          ) >> IO.raiseError(AppError2.AuthError("Invalid token"))
      }
    } yield parsedDetails
  }

  //TODO actually call the db and fetch roles for user
  def login(
      user: UserLogin
  )(implicit logger: Logger[Task]): IO[AppError2, JWTMac[HMACSHA256]] = {
    for {
      jwt <-
        if (user.password.inner.value === "hello")
          encode[Task](
            user
              .into[User]
              .withFieldConst(_.id, Refinements.UserId(1))
              .withFieldConst(_.role, UserRole.User)
              .transform
          ).hideErrors
        else
          IO.raiseError(AppError2.AuthError("Invalid password"))
      _ <- C.put(Refinements.UserId(1), jwt)
    } yield jwt
  }
}
