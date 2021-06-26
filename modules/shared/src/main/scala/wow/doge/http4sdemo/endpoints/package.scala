package wow.doge.http4sdemo

import io.circe.generic.semiauto._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.codec.newtype._
import sttp.tapir.json.circe._
import wow.doge.http4sdemo.AppError

package object endpoints {
  val reqCtxEndpoint = endpoint.in(
    extractFromRequest(_.header("X-request-id"))
      .and(extractFromRequest(r => r.method))
      .and(extractFromRequest(r => Option(r.uri.getPath())))
      .and(extractFromRequest(r => Option(r.uri.getQuery())))
      .map(ReqContext.tapirMapping)
  )

  val baseEndpoint = reqCtxEndpoint
    .in("api")
    .errorOut(
      oneOf[AppError](
        statusMappingValueMatcher(
          StatusCode.NotFound,
          jsonBody[AppError].description("not found")
        ) {
          case e: AppError.EntityDoesNotExist => true
          case _                              => false
        },
        statusMappingValueMatcher(
          StatusCode.BadRequest,
          jsonBody[AppError].description("bad request")
        ) {
          case e: AppError.EntityAlreadyExists => true
          case e: AppError.BadInput            => true
          case _                               => false
        },
        statusMappingValueMatcher(
          StatusCode.Unauthorized,
          jsonBody[AppError].description("forbidden")
        ) {
          case e: AppError.AuthError => true
          case _                     => false
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
          jsonBody[AppError]
            .description(
              "Internal Error case implying some error case was uncaught"
            )
        )
      )
    )

  val basePublicEndpoint = baseEndpoint.in("public")

  val basePrivateEndpoint =
    baseEndpoint.in("private").in(AuthDetails.endpointIn)
}

package endpoints {
  // import sttp.tapir.annotations.{header => _header}
  import sttp.tapir.annotations.{bearer => _bearer}
  import sttp.tapir.annotations.deriveEndpointInput
  import sttp.tapir.Mapping
  import sttp.model.Method
  import wow.doge.http4sdemo.refinements.Refinements._
  import wow.doge.http4sdemo.utils.mytapir._
  final case class ReqContext(
      reqId: String,
      reqMethod: Method,
      reqUri: String,
      reqParams: String
  )
  object ReqContext {

    val empty = ReqContext("null", Method("null"), "null", "null")

    implicit val tapirMapping = Mapping.fromDecode[
      (Option[String], Method, Option[String], Option[String]),
      ReqContext
    ] { case (reqId, reqMethod, reqUri, reqParams) =>
      sttp.tapir.DecodeResult.Value(
        ReqContext(
          reqId.getOrElse("null"),
          reqMethod,
          reqUri.getOrElse("null"),
          reqParams.getOrElse("null")
        )
      )
    } { ctx =>
      (
        Some(ctx.reqId),
        ctx.reqMethod,
        Some(ctx.reqUri),
        Some(ctx.reqParams)
      )
    }
  }

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

  final case class RegistrationResponse(userId: UserId)
  object RegistrationResponse {
    implicit val codec = deriveCodec[RegistrationResponse]
    implicit val schema = Schema.derived[RegistrationResponse]
  }
}
