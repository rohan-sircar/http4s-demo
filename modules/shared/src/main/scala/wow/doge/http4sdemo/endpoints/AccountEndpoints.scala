package wow.doge.http4sdemo.endpoints

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.codec.newtype._
import sttp.tapir.json.circe._
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
    .out(jsonBody[RegistrationResponse])
  // .out(stringBody.and(statusCode(StatusCode.Ok)))
}
