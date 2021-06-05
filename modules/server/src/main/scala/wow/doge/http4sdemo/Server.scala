package wow.doge.http4sdemo

import cats.effect.Resource
import cats.implicits._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.SharedMetricRegistries
import io.odin.meta.Position
import io.odin.meta.Render
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
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.routes.LibraryRoutes
import wow.doge.http4sdemo.routes.LibraryRoutes2
import wow.doge.http4sdemo.routes.LoginRoutes
import wow.doge.http4sdemo.schedulers.Schedulers
import wow.doge.http4sdemo.server.auth.AuthService
import wow.doge.http4sdemo.server.auth.JwtSigningKey
import wow.doge.http4sdemo.server.config.HttpConfig
import wow.doge.http4sdemo.server.repos.InMemoryCredentialsRepo
import wow.doge.http4sdemo.server.utils.StructuredOdinLogger
import wow.doge.http4sdemo.server.{ExtendedPgProfile => JdbcProfile}
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryServiceImpl

final class Server(
    db: DatabaseDef,
    p: JdbcProfile,
    schedulers: Schedulers,
    config: HttpConfig
)(implicit logger: io.odin.Logger[Task]) {
  private val log: String => Task[Unit] = str =>
    logger.info(str)(
      Render[String],
      Position(
        fileName = "httpLogger",
        enclosureName = "httpLogger",
        packageName = "httpLogger",
        line = -1
      )
    )

  val resource =
    for {
      _ <- Resource.eval(Task.unit)
      registry <- Resource.eval(
        Task(SharedMetricRegistries.getOrCreate("default"))
      )
      metricsRoute = metricsRoutes(registry)
      libraryDbio = new LibraryDbio(p)
      libraryService = new LibraryServiceImpl(p, libraryDbio, db)
      credentialsRepo <- Resource.eval(InMemoryCredentialsRepo())
      _key <- Resource.eval(HMACSHA256.generateKey[Task])
      key = JwtSigningKey(_key)
      authService = new AuthService(credentialsRepo)(key)
      httpRoutes = Metrics(Dropwizard[Task](registry, "server"))(
        new LibraryRoutes(libraryService)(logger).routes <+> new LibraryRoutes2(
          libraryService,
          authService
        )(logger).routes <+> new LoginRoutes(authService)(logger).routes
      )
      httpApp2 <- Resource.eval(
        Throttle(config.throttle.amount.value, config.throttle.per)(
          Timeout(config.timeout)(
            AutoSlash.httpRoutes(metricsRoute <+> httpRoutes).orNotFound
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
        .withLogger(new StructuredOdinLogger(logger, "org.http4s.ember"))
        .build
    } yield server

  def metricsRoutes(registry: MetricRegistry) = HttpRoutes.of[Task] {
    case req if req.method === Method.GET && req.uri === uri"/api/metrics" =>
      metricsResponse(registry)
  }

}
