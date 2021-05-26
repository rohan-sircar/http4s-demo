package wow.doge.http4sdemo

import cats.effect.Resource
import cats.implicits._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.SharedMetricRegistries
import monix.bio.Task
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.metrics.dropwizard.Dropwizard
import org.http4s.metrics.dropwizard.metricsResponse
import org.http4s.server.middleware.AutoSlash
import org.http4s.server.middleware.Metrics
import org.http4s.server.middleware.RequestId
import org.http4s.server.middleware.RequestLogger
import org.http4s.server.middleware.ResponseLogger
import org.http4s.server.middleware.ResponseTiming
import org.http4s.server.middleware.Throttle
import org.http4s.server.middleware.Timeout
import slick.jdbc.JdbcBackend.DatabaseDef
import wow.doge.http4sdemo.server.utils.StructuredOdinLogger
import wow.doge.http4sdemo.server.utils.config.HttpConfig
import wow.doge.http4sdemo.profile.{ExtendedPgProfile => JdbcProfile}
import wow.doge.http4sdemo.routes.LibraryRoutes
import wow.doge.http4sdemo.schedulers.Schedulers
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryServiceImpl

final class Server(
    db: DatabaseDef,
    p: JdbcProfile,
    schedulers: Schedulers,
    config: HttpConfig
)(implicit logger: io.odin.Logger[Task]) {
  private val log: String => Task[Unit] = str => logger.info(str)

  val resource =
    for {
      _ <- Resource.eval(Task.unit)
      registry <- Resource.eval(
        Task(SharedMetricRegistries.getOrCreate("default"))
      )
      metricsRoute = metricsRoutes(registry)
      libraryDbio = new LibraryDbio(p)
      libraryService = new LibraryServiceImpl(p, libraryDbio, db, logger)
      httpApp = Metrics(Dropwizard[Task](registry, "server"))(
        new LibraryRoutes(libraryService).routes
      )
      httpApp2 <- Resource.eval(
        Throttle(config.throttle.amount.value, config.throttle.per)(
          Timeout(config.timeout)(
            AutoSlash.httpRoutes(metricsRoute <+> httpApp).orNotFound
          )
        )
      )
      finalHttpApp =
        ResponseLogger.httpApp(true, false, logAction = log.pure[Option])(
          RequestId(
            RequestLogger.httpApp(true, false, logAction = log.pure[Option])(
              ResponseTiming(httpApp2)
            )
          )
        )
      server <- EmberServerBuilder
        .default[Task]
        .withHost("0.0.0.0")
        .withPort(8081)
        .withHttpApp(finalHttpApp)
        .withBlocker(schedulers.io.blocker)
        .withLogger(new StructuredOdinLogger(logger))
        .build
    } yield server

  def metricsRoutes(registry: MetricRegistry) = HttpRoutes.of[Task] {
    case req if req.method === Method.GET && req.uri === uri"/api/metrics" =>
      metricsResponse(registry)
  }

}
