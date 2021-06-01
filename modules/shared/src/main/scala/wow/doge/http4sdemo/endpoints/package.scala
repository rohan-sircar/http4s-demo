package wow.doge.http4sdemo

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

  val errorEndpoint = reqCtxEndpoint.errorOut(
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
      // statusMapping(
      //   StatusCode.Unauthorized,
      //   jsonBody[Unauthorized].description("unauthorized")
      // ),
      // statusMapping(
      //   StatusCode.NoContent,
      //   emptyOutput.map(_ => NoContent)(_ => ())
      // ),
      statusDefaultMapping(jsonBody[AppError2].description("unknown"))
    )
  )
}
