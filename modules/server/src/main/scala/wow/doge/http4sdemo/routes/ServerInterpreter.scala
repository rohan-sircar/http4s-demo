package wow.doge.http4sdemo.routes

import cats.syntax.all._
import io.odin.Logger
import io.odin.syntax._
import monix.bio.Task
import shapeless.syntax.std.product._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.utils.ReqContext

trait ServerInterpreter extends Http4sServerInterpreter {
  def logger: Logger[Task]

  def enrichLogger(ctx: ReqContext) = Task(
    logger
      .withConstContext(ctx.toMap.map { case k -> v =>
        k.name -> v.toString
      })
      .asRight[AppError2]
  )
}
