package wow.doge.http4sdemo.app
import monix.bio.Task
import org.http4s.Request
import org.http4s.server.middleware.RequestId
package object utils {
  def extractReqId(req: Request[Task]) =
    req.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("null")
}
