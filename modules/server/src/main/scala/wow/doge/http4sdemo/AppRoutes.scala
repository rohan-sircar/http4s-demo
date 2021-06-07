package wow.doge.http4sdemo

import cats.syntax.all._
import com.codahale.metrics.MetricRegistry
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import org.http4s.metrics.dropwizard.Dropwizard
import org.http4s.server.middleware.Metrics
import slick.jdbc.JdbcBackend.DatabaseDef
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.routes.AccountRoutes
import wow.doge.http4sdemo.routes.LibraryRoutes
import wow.doge.http4sdemo.routes.LibraryRoutes2
import wow.doge.http4sdemo.server.auth.JwtSigningKey
import wow.doge.http4sdemo.server.repos.InMemoryCredentialsRepo
import wow.doge.http4sdemo.server.repos.UsersDbio
import wow.doge.http4sdemo.server.repos.UsersRepo
import wow.doge.http4sdemo.server.services.AuthServiceImpl
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryServiceImpl

final class AppRoutes(db: DatabaseDef, registry: MetricRegistry)(implicit
    logger: Logger[Task]
) {
  val routes = for {
    _ <- IO.unit
    libraryDbio = new LibraryDbio
    libraryService = new LibraryServiceImpl(libraryDbio, db)
    credentialsRepo <- InMemoryCredentialsRepo()
    _key <- HMACSHA256.generateKey[Task].hideErrors
    key = JwtSigningKey(_key)
    usersDbio = new UsersDbio
    usersRepo = new UsersRepo(db, usersDbio)
    authService = new AuthServiceImpl(credentialsRepo, usersRepo)(key)
    httpRoutes = Metrics(Dropwizard[Task](registry, "server"))(
      new LibraryRoutes(libraryService, authService)(logger).routes <+>
        new LibraryRoutes2(libraryService, authService)(logger).routes <+>
        new AccountRoutes(authService)(logger).routes
    )
  } yield httpRoutes

}
