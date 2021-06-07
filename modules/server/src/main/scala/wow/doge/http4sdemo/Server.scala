package wow.doge.http4sdemo

import cats.effect.Resource
import cats.syntax.all._
import com.codahale.metrics.MetricRegistry
import io.odin.meta.Position
import io.odin.meta.Render
import monix.bio.Task
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.metrics.dropwizard.metricsResponse
import org.http4s.server.middleware.AutoSlash
import org.http4s.server.middleware.RequestId
import org.http4s.server.middleware.RequestLogger
import org.http4s.server.middleware.ResponseLogger
import org.http4s.server.middleware.ResponseTiming
import org.http4s.server.middleware.Throttle
import org.http4s.server.middleware.Timeout
import wow.doge.http4sdemo.AppRoutes
import wow.doge.http4sdemo.schedulers.Schedulers
import wow.doge.http4sdemo.server.config.HttpConfig
import wow.doge.http4sdemo.server.utils.GlobalErrorHandler
import wow.doge.http4sdemo.server.utils.StructuredOdinLogger

final class Server(
    schedulers: Schedulers,
    appRoutes: AppRoutes,
    registry: MetricRegistry,
    config: HttpConfig
)(implicit logger: io.odin.Logger[Task]) {
  private val requestLog: String => Task[Unit] = str =>
    logger.info(str)(
      Render[String],
      Position(
        fileName = "httpRequestLogger",
        enclosureName = "httpRequestLogger",
        packageName = "httpRequestLogger",
        line = -1
      )
    )

  private val responseLog: String => Task[Unit] = str =>
    logger.info(str)(
      Render[String],
      Position(
        fileName = "httpResponseLogger",
        enclosureName = "httpResponseLogger",
        packageName = "httpResponseLogger",
        line = -1
      )
    )

  val resource =
    for {
      _ <- Resource.eval(Task.unit)
      metricsRoute = metricsRoutes(registry)
      appRoutes <- Resource.eval(appRoutes.routes)
      httpApp <- Resource.eval(
        Throttle(config.throttle.amount.value, config.throttle.per)(
          Timeout(config.timeout)(
            AutoSlash.httpRoutes(metricsRoute <+> appRoutes).orNotFound
          )
        )
      )
      finalHttpApp =
        ResponseLogger.httpApp(
          true,
          false,
          logAction = responseLog.pure[Option]
        )(
          RequestId(
            RequestLogger.httpApp(
              true,
              false,
              logAction = requestLog.pure[Option]
            )(ResponseTiming(GlobalErrorHandler(httpApp)(logger)))
          )
        )
      server <- EmberServerBuilder
        .default[Task]
        .withHost("0.0.0.0")
        .withPort(8081)
        .withHttpApp(finalHttpApp)
        .withBlocker(schedulers.io.blocker)
        .withLogger(new StructuredOdinLogger(logger, "org.http4s.ember"))
        .build
    } yield server

  def metricsRoutes(registry: MetricRegistry) = HttpRoutes.of[Task] {
    case req if req.method === Method.GET && req.uri === uri"/api/metrics" =>
      metricsResponse(registry)
  }

}
