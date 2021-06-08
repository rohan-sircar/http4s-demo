package wow.doge.http4sdemo.server

import cats.effect.Sync
import cats.syntax.all._
import io.circe.syntax._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import tsec.jws.mac._
import tsec.jwt._
import tsec.mac.jca._
import tsec.passwordhashers.jca.BCrypt
import wow.doge.http4sdemo.models.User
import wow.doge.http4sdemo.refinements.Refinements

import scala.concurrent.duration._
import wow.doge.http4sdemo.refinements.HashedPasswordRefinement
import wow.doge.http4sdemo.models.UserLogin
import tsec.passwordhashers.PasswordHash

package object auth {
  def decode[F[_]: Sync](token: String)(implicit key: JwtSigningKey) =
    for {
      parsed <- JWTMac.verifyAndParse[F, HMACSHA256](token, key.inner)
      user <- parsed.body.getCustomF[F, User](User.Claim)
    } yield VerifiedAuthDetails(parsed, user)

  def encode[F[_]: Sync](user: User)(implicit key: JwtSigningKey) =
    for {
      _ <- Sync[F].unit
      claims <- JWTClaims
        .withDuration[F](
          issuedAt = Some(0.minutes),
          //   notBefore = Some(5.seconds),
          expiration = Some(10.minutes),
          customFields = List(User.Claim -> user.asJson)
        )
      jwt <- JWTMac.build[F, HMACSHA256](claims, key.inner)
    } yield jwt

  def hashPassword[F[_]: Sync](
      unhashed: Refinements.UnhashedUserPassword
  ): F[Either[String, Refinements.HashedUserPassword]] = {
    for {
      rawHash <- BCrypt.hashpw[F](unhashed.inner.value)
      res = HashedPasswordRefinement
        .from(rawHash)
        .map(Refinements.HashedUserPassword.apply)
    } yield res
  }

  def hashPasswordIO(
      unhashed: Refinements.UnhashedUserPassword
  ): UIO[Refinements.HashedUserPassword] =
    hashPassword[Task](unhashed).hideErrors
      .flatMap(IO.fromEither)
      .mapError(s => new Exception(s))
      .hideErrors

  def checkPasswordIO(userLogin: UserLogin, user: User) = BCrypt
    .checkpw[Task](
      userLogin.password.inner.value,
      PasswordHash[BCrypt](user.password.inner.value)
    )
    .hideErrors

}

package auth {
  final case class JwtSigningKey(inner: MacSigningKey[HMACSHA256])

  final case class VerifiedAuthDetails(jwt: JWTMac[HMACSHA256], user: User)
}
