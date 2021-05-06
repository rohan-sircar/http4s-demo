package wow.doge.http4sdemo

import cats.implicits._
import fs2.Stream
import io.odin
import monix.bio.Task
import monix.execution.Scheduler
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.routes.LibraryRoutes
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryServiceImpl

final class Server(db: DatabaseDef, p: JdbcProfile, logger: odin.Logger[Task]) {

  def stream(implicit s: Scheduler): Stream[Task, Nothing] = {
    val logger = io.odin.consoleLogger[Task](formatter =
      io.odin.formatter.Formatter.colorful
    )
    val log: String => Task[Unit] = str => logger.debug(str)
    for {
      client <- BlazeClientBuilder[Task](s).stream
      libraryDbio = new LibraryDbio(p)
      libraryService = new LibraryServiceImpl(p, libraryDbio, db)
      httpApp = (
        new LibraryRoutes(libraryService, logger).routes
      ).orNotFound
      finalHttpApp = Logger.httpApp(
        true,
        true,
        logAction = log.pure[Option]
      )(httpApp)
      exitCode <- BlazeServerBuilder[Task](s)
        .bindHttp(8081, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
