package wow.doge.http4sdemo

import cats.implicits._
import fs2.Stream
import monix.bio.Task
import monix.execution.Scheduler
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryService

object Http4sdemoServer {

  def stream(
      db: DatabaseDef,
      p: JdbcProfile
  )(implicit s: Scheduler): Stream[Task, Nothing] = {
    for {
      client <- BlazeClientBuilder[Task](s).stream
      helloWorldAlg = HelloWorld.impl
      jokeAlg = Jokes.impl(client)
      ss = new UserService(p, db)
      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.

      libraryDbio = new LibraryDbio(p)
      libraryService = new LibraryService(p, libraryDbio, db)
      httpApp = (
        Http4sdemoRoutes.helloWorldRoutes[Task](helloWorldAlg) <+>
          Http4sdemoRoutes.jokeRoutes[Task](jokeAlg) <+>
          Http4sdemoRoutes.userRoutes(ss) <+>
          Http4sdemoRoutes.libraryRoutes(libraryService)
      ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[Task](s)
        .bindHttp(8081, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
