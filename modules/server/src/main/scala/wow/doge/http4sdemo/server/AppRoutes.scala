package wow.doge.http4sdemo.server

import cats.syntax.all._
import com.codahale.metrics.MetricRegistry
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.implicits._
import org.http4s.metrics.dropwizard.Dropwizard
import org.http4s.metrics.dropwizard.metricsResponse
import org.http4s.server.middleware.Metrics
import org.http4s.server.middleware.Timeout
import slick.jdbc.JdbcBackend.DatabaseDef
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.server.auth.JwtSigningKey
import wow.doge.http4sdemo.server.config.HttpConfig
import wow.doge.http4sdemo.server.repos.InMemoryCredentialsRepo
import wow.doge.http4sdemo.server.repos.UsersDbio
import wow.doge.http4sdemo.server.repos.UsersRepo
import wow.doge.http4sdemo.server.routes.AccountRoutes
import wow.doge.http4sdemo.server.routes.LibraryRoutes
import wow.doge.http4sdemo.server.routes.LibraryRoutes2
import wow.doge.http4sdemo.server.services.AuthServiceImpl
import wow.doge.http4sdemo.server.services.LibraryDbio
import wow.doge.http4sdemo.server.services.LibraryServiceImpl
import java.nio.charset.StandardCharsets
import wow.doge.http4sdemo.server.config.AuthConfig

final class AppRoutes(
    db: DatabaseDef,
    registry: MetricRegistry,
    httpConfig: HttpConfig,
    authConfig: AuthConfig
)(implicit logger: Logger[Task]) {
  val routes = for {
    _ <- IO.unit
    libraryDbio = new LibraryDbio
    libraryService = new LibraryServiceImpl(libraryDbio, db)
    credentialsRepo <- InMemoryCredentialsRepo()
    keyStr = "ROlJpvBtrqrS7qrUQNXExZUTvTRrnpNm9QjpZrVsXjSJvR5ZGP"
    _key <- HMACSHA256.buildKey[Task](keyStr.getBytes(StandardCharsets.UTF_8))
    // _key <- HMACSHA256.generateKey[Task].hideErrors
    key = JwtSigningKey(_key)
    usersDbio = new UsersDbio
    usersRepo = new UsersRepo(db, usersDbio)
    authService = new AuthServiceImpl(credentialsRepo, usersRepo, authConfig)(
      key
    )
    apiRoutes = Metrics(Dropwizard[Task](registry, "server"))(
      Timeout(httpConfig.timeout)(
        new LibraryRoutes(libraryService, authService)(logger).routes <+>
          new LibraryRoutes2(libraryService, authService)(logger).routes <+>
          new AccountRoutes(authService)(logger).routes
      )
    )
    httpRoutes = apiRoutes <+> metricsRoutes(registry)
  } yield httpRoutes

  def metricsRoutes(registry: MetricRegistry) = HttpRoutes.of[Task] {
    case req if req.method === Method.GET && req.uri === uri"/api/metrics" =>
      metricsResponse(registry)
  }

}
