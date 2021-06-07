package wow.doge.http4sdemo.routes

import cats.Applicative
import cats.syntax.all._
import io.odin.Logger
import io.odin.syntax._
import monix.bio.IO
import monix.bio.Task
import shapeless.syntax.std.product._
import sttp.tapir.server.LogRequestHandling
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.endpoints.AuthDetails
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.schedulers.Schedulers
import wow.doge.http4sdemo.server.auth.VerifiedAuthDetails
import wow.doge.http4sdemo.server.services.AuthService
import wow.doge.http4sdemo.utils.ReqContext

trait ServerInterpreter extends Http4sServerInterpreter {
  def logger: Logger[Task]

  def debugLog(msg: String, exOpt: Option[Throwable]): Task[Unit] =
    exOpt match {
      case None     => logger.debug(msg)
      case Some(ex) => logger.debug(msg, ex)
    }

  val logRequestHandler = LogRequestHandling[Task[Unit]](
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogLogicExceptions =
      (msg: String, ex: Throwable) => logger.error(msg, ex),
    noLog = Applicative[Task].unit,
    logAllDecodeFailures = false,
    logLogicExceptions = true,
    logWhenHandled = false
  )

  implicit val options: Http4sServerOptions[Task] = Http4sServerOptions
    .default[Task]
    .copy(
      blockingExecutionContext = Schedulers.default.io.value,
      logRequestHandling = logRequestHandler
    )

  def enrichLogger(ctx: ReqContext) = Task(
    logger
      .withConstContext(ctx.toMap.map { case k -> v =>
        k.name -> v.toString
      })
      .asRight[AppError2]
  )
}

trait AuthedServerInterpreter extends ServerInterpreter {

  def authService: AuthService

  def authorize[T](ctx: ReqContext, details: AuthDetails)(role: UserRole)(
      f: (Logger[Task], VerifiedAuthDetails) => IO[AppError2, T]
  ) =
    for {
      logger <- enrichLogger(ctx).hideErrors.rethrow
      verified <- authService.verify(details)(logger)
      logger <- IO.pure(
        logger.withConstContext(
          Map("userId" -> verified.user.id.inner.value.toString)
        )
      )
      res <-
        if (verified.user.role.value <= role.value) f(logger, verified)
        else
          IO.raiseError(
            AppError2
              .AuthError("Inadequate privileges for accessing this resource")
          )
    } yield res

}
