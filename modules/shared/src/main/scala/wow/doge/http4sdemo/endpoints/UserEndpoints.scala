package wow.doge.http4sdemo.endpoints

import wow.doge.http4sdemo.models._
import wow.doge.http4sdemo.utils.mytapir._

object UserEndpoints {
  val baseUserEndpoint = basePrivateEndpoint.in("user")

  val getUserEndpoint = baseUserEndpoint.get
    .description("Get current authenticated user")
    .out(jsonBody[UserIdentity])

}
