package wow.doge.http4sdemo

import cats.effect.Resource
import cats.implicits._
import monix.bio.Task
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.http4s.server.middleware.RequestId
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
      libraryDbio = new LibraryDbio(p)
      libraryService = new LibraryServiceImpl(p, libraryDbio, db, logger)
      httpApp = (
        new LibraryRoutes(libraryService, logger).routes
      ).orNotFound
      finalHttpApp =
        Logger.httpApp(
          true,
          false,
          logAction = log.pure[Option]
        )(RequestId(httpApp))

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
