package wow.doge.http4sdemo

import scala.concurrent.duration._

import cats.effect.Resource
import cats.implicits._
import com.codahale.metrics.SharedMetricRegistries
import monix.bio.Task
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.metrics.dropwizard.Dropwizard
import org.http4s.metrics.dropwizard.metricsResponse
import org.http4s.server.middleware.AutoSlash
import org.http4s.server.middleware.Logger
import org.http4s.server.middleware.Metrics
import org.http4s.server.middleware.RequestId
import org.http4s.server.middleware.ResponseTiming
import org.http4s.server.middleware.Throttle
import org.http4s.server.middleware.Timeout
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.routes.LibraryRoutes
import wow.doge.http4sdemo.schedulers.Schedulers
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryServiceImpl
import wow.doge.http4sdemo.utils.StructuredOdinLogger
final class Server(
    db: DatabaseDef,
    p: JdbcProfile,
    logger: io.odin.Logger[Task],
    schedulers: Schedulers
) {
  private val log: String => Task[Unit] = str => logger.debug(str)

  def resource =
    for {
      _ <- Resource.liftF(Task.unit)
      registry = SharedMetricRegistries.getOrCreate("default")
      metricsRoute = HttpRoutes.of[Task] {
        case req
            if req.method === Method.GET && req.uri === uri"/api/metrics" =>
          metricsResponse(registry)
      }
      libraryDbio = new LibraryDbio(p)
      libraryService = new LibraryServiceImpl(p, libraryDbio, db, logger)
      httpApp = Metrics(Dropwizard[Task](registry, "server"))(
        new LibraryRoutes(libraryService, logger).routes
      )
      httpApp2 <- Resource.liftF(
        Throttle.apply(10, 1.second)(
          Timeout(15.seconds)(
            AutoSlash.httpRoutes(metricsRoute <+> httpApp).orNotFound
          )
        )
      )
      finalHttpApp =
        Logger.httpApp(true, false, logAction = log.pure[Option])(
          ResponseTiming(RequestId(httpApp2))
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

}
