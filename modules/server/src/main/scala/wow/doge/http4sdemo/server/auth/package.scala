package wow.doge.http4sdemo.server

import scala.concurrent.duration._

import cats.effect.Sync
import cats.syntax.all._
import io.circe.syntax._
import tsec.jws.mac._
import tsec.jwt._
import tsec.mac.jca._
import wow.doge.http4sdemo.models.User

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
}

package auth {
  final case class JwtSigningKey(inner: MacSigningKey[HMACSHA256])

  final case class VerifiedAuthDetails(jwt: JWTMac[HMACSHA256], user: User)
}
