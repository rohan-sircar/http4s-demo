package wow.doge.http4sdemo.server

import eu.timepit.refined.auto._
import org.http4s.Uri
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.MonixBioSuite
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.auth.JwtSigningKey

trait UnitTestBase extends MonixBioSuite {
  val Root = Uri(path = "")

  val regularNewUser = NewUser(
    Username("regular-user"),
    HashedUserPassword(
      "$2a$10$V2qon2elG0P6/u5J.5xyDOx/.S94Cnt0iod64qAUhbGo6C8UCQOcm"
    ),
    UserRole.User
  )

  val adminNewUser = NewUser(
    Username("admin-user"),
    HashedUserPassword(
      "$2a$10$V2qon2elG0P6/u5J.5xyDOx/.S94Cnt0iod64qAUhbGo6C8UCQOcm"
    ),
    UserRole.Admin
  )

  val suNewUser = NewUser(
    Username("admin-user"),
    HashedUserPassword(
      "$2a$10$V2qon2elG0P6/u5J.5xyDOx/.S94Cnt0iod64qAUhbGo6C8UCQOcm"
    ),
    UserRole.SuperUser
  )

  val testSecretKey = JwtSigningKey(HMACSHA256.unsafeGenerateKey)
}
