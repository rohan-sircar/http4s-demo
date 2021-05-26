package wow.doge.http4sdemo.server
import monix.bio.Task
import org.http4s.Request
import org.http4s.server.middleware.RequestId
import cats.Show
import io.odin.Logger
import io.odin.syntax._
import cats.syntax.show._
package object utils {
  def extractReqId(req: Request[Task]) =
    req.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("null")

  def enrichLogger[S](
      req: Request[Task],
      additionalContext: Map[String, S] = Map.empty[String, String]
  )(implicit S: Show[S], L: Logger[Task]) = {
    L.withConstContext(
      Map(
        "request-id" -> extractReqId(req),
        "request-uri" -> req.uri.path
      ) ++ req.uri.query.multiParams.map { case key -> value =>
        key -> value.show
      }.toMap ++ additionalContext.map { case key -> value =>
        key -> value.show
      }.toMap
    )
  }
}
