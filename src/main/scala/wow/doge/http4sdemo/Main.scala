package wow.doge.http4sdemo

import cats.effect.ExitCode
import cats.effect.Resource
import monix.bio.BIOApp
import monix.bio.Task
import monix.bio.UIO
import slick.jdbc.JdbcProfile
import wow.doge.http4sdemo.SlickResource

object Main extends BIOApp {
  val profile: JdbcProfile = _root_.slick.jdbc.H2Profile
  def app = for {
    db <- SlickResource("myapp.database")
    _ <- Resource.liftF(for {
      config <- JdbcDatabaseConfig.loadFromGlobal("myapp.database")
      _ <- DBMigrations.migrate(config)
    } yield ())
    _ <- Resource.liftF(
      Task.deferAction(implicit s =>
        Http4sdemoServer.stream(db, profile).compile.drain
      )
    )
  } yield ()
  def run(args: List[String]) = {
    app
      .use(_ => Task.never)
      .onErrorHandleWith(ex => UIO(ex.printStackTrace()))
      .as(ExitCode.Success)
  }
}
