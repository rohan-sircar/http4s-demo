package wow.doge.http4sdemo.server

import eu.timepit.refined.auto._
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.Uri
import org.http4s.headers.Authorization
import wow.doge.http4sdemo.MonixBioSuite
import wow.doge.http4sdemo.models.NewUser
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.auth.JwtToken

trait UnitTestBase extends MonixBioSuite {
  val Root = Uri(path = "")

  val dummyUserPassword = HashedUserPassword(
    "$2a$10$V2qon2elG0P6/u5J.5xyDOx/.S94Cnt0iod64qAUhbGo6C8UCQOcm"
  )

  val regularNewUser =
    NewUser(
      Username("regular-user"),
      dummyUserPassword,
      UserEmail("rohansircar@protonmail.com"),
      UserRole.User
    )

  val adminNewUser =
    NewUser(
      Username("admin-user"),
      dummyUserPassword,
      UserEmail("rohansircar@protonmail.com"),
      UserRole.Admin
    )

  val suNewUser =
    NewUser(
      Username("super-user"),
      dummyUserPassword,
      UserEmail("rohansircar@protonmail.com"),
      UserRole.SuperUser
    )

  def authHeader(token: JwtToken) = Authorization(
    Credentials.Token(AuthScheme.Bearer, token.inner)
  )

  def authHeaderFromTokenStr(token: String) = Authorization(
    Credentials.Token(AuthScheme.Bearer, token)
  )
}
