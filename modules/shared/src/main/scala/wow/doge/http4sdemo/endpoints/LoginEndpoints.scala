package wow.doge.http4sdemo.endpoints

import sttp.tapir._
import sttp.tapir.codec.newtype._
import sttp.tapir.json.circe._
import wow.doge.http4sdemo.models._

object LoginEndpoints {
  val loginEndpoint = baseEndpoint
    .in("api" / "login")
    .in(jsonBody[UserLogin])
    .out(jsonBody[LoginResponse])
}
