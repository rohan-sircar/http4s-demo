package wow.doge.http4sdemo.server.utils

import cats.data.Kleisli
import io.odin.Logger
import monix.bio.Cause
import monix.bio.IO
import monix.bio.Task
import org.http4s.HttpApp
import org.http4s.Response
import org.http4s.Status
import org.http4s.Request

object GlobalErrorHandler {
  def contextFromReq(req: Request[Task]) = {
    Map(
      "reqMethod" -> req.method.name,
      "reqUri" -> req.uri.path,
      "reqId" -> extractReqId(req)
    )
  }
  def apply(
      httpApp: HttpApp[Task]
  )(implicit logger: Logger[Task]): HttpApp[Task] = Kleisli { req =>
    httpApp(req).redeemCauseWith(
      {
        case Cause.Error(err) =>
          logger
            .error("Unhandled error:", contextFromReq(req), err)
            .hideErrors >> IO.pure(
            Response(
              Status.InternalServerError,
              body = fs2
                .Stream("Internal server error")
                .through(fs2.text.utf8Encode)
            )
          )
        case Cause.Termination(err) =>
          logger
            .error("Terminal error:", contextFromReq(req), err)
            .hideErrors >> IO.pure(
            Response(
              Status.InternalServerError,
              body = fs2
                .Stream("Internal server error")
                .through(fs2.text.utf8Encode)
            )
          )
      },
      IO.pure
    )
  }
}
