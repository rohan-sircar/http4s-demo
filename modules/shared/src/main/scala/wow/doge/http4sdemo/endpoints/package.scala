package wow.doge.http4sdemo

import io.circe.generic.semiauto._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.codec.newtype._
import sttp.tapir.json.circe._
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.utils.ReqContext

package object endpoints {
  val reqCtxEndpoint = endpoint.in(
    extractFromRequest(_.header("X-request-id"))
      .and(extractFromRequest(r => Option(r.uri.getPath())))
      .and(extractFromRequest(r => Option(r.uri.getQuery())))
      .map(ReqContext.tapirMapping)
  )

  val baseEndpoint = reqCtxEndpoint.errorOut(
    oneOf[AppError2](
      statusMappingValueMatcher(
        StatusCode.NotFound,
        jsonBody[AppError2].description("not found")
      ) {
        case e: AppError2.EntityDoesNotExist => true
        case _                               => false
      },
      statusMappingValueMatcher(
        StatusCode.BadRequest,
        jsonBody[AppError2].description("bad request")
      ) {
        case e: AppError2.EntityAlreadyExists => true
        case e: AppError2.BadInput            => true
        case _                                => false
      },
      statusMappingValueMatcher(
        StatusCode.Unauthorized,
        jsonBody[AppError2].description("forbidden")
      ) {
        case e: AppError2.AuthError => true
        case _                      => false
      },
      // statusMapping(
      //   StatusCode.Unauthorized,
      //   jsonBody[Unauthorized].description("unauthorized")
      // ),
      // statusMapping(
      //   StatusCode.NoContent,
      //   emptyOutput.map(_ => NoContent)(_ => ())
      // ),
      statusDefaultMapping(
        jsonBody[AppError2]
          .description(
            "Internal Error case implying some error case was uncaught"
          )
      )
    )
  )

  val basePublicEndpoint = baseEndpoint.in("api" / "public")

  val basePrivateEndpoint =
    baseEndpoint.in("api" / "private").in(AuthDetails.endpointIn)
}

package endpoints {
  // import sttp.tapir.annotations.{header => _header}
  import sttp.tapir.annotations.{bearer => _bearer}
  import sttp.tapir.annotations.deriveEndpointInput

  final case class AuthDetails(@_bearer bearerToken: String)
  object AuthDetails {
    implicit val codec = deriveCodec[AuthDetails]
    implicit val endpointIn = deriveEndpointInput[AuthDetails]
  }

  final case class LoginResponse(token: String)
  object LoginResponse {
    implicit val codec = deriveCodec[LoginResponse]
    implicit val schema = Schema.derived[LoginResponse]
  }

  final case class RegistrationResponse(message: String)
  object RegistrationResponse {
    implicit val codec = deriveCodec[RegistrationResponse]
    implicit val schema = Schema.derived[RegistrationResponse]
  }
}
