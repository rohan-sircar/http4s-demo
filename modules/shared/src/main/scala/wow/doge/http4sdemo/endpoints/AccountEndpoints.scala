package wow.doge.http4sdemo.endpoints

import io.chrisdavenport.fuuid.FUUID
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.codec.newtype._
import sttp.tapir.json.circe._
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models._

object AccountEndpoints {

  val loginEndpoint = baseEndpoint.post
    .in("login")
    .in(jsonBody[UserLogin])
    .out(jsonBody[LoginResponse])

  val logoutEndpoint = basePrivateEndpoint.post
    .in("logout")
    .out(statusCode(StatusCode.NoContent))

  val registrationEndpoint = baseEndpoint.post
    .in("register")
    .in(jsonBody[UserRegistration])
    .out(jsonBody[RegistrationResponse].and(statusCode(StatusCode.Created)))
  // .out(stringBody.and(statusCode(StatusCode.Ok)))

  val accountActivationRoute = baseEndpoint.post
    .in("activate-account")
    .in(path[FUUID])

}
