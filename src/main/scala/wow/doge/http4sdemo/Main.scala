package wow.doge.http4sdemo

import scala.concurrent.duration.MILLISECONDS

import cats.effect.ExitCode
import cats.effect.Resource
import io.odin.Level
import io.odin.consoleLogger
import io.odin.formatter.Formatter
import io.odin.syntax._
import monix.bio.BIOApp
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import slick.jdbc.JdbcProfile

object Main extends BIOApp {
  val profile: JdbcProfile = slick.jdbc.PostgresProfile
  val app = for {
    startTime <- Resource.liftF(IO.clock.realTime(MILLISECONDS))
    _ <- Resource.liftF(Task(println("""
    |        .__     __    __            _____                      .___                     
    |        |  |___/  |__/  |_______   /  |  |  ______           __| _/____   _____   ____  
    |        |  |  \   __\   __\____ \ /   |  |_/  ___/  ______  / __ |/ __ \ /     \ /  _ \ 
    |        |   Y  \  |  |  | |  |_> >    ^   /\___ \  /_____/ / /_/ \  ___/|  Y Y  (  <_> )
    |        |___|  /__|  |__| |   __/\____   |/____  >         \____ |\___  >__|_|  /\____/ 
    |            \/           |__|        |__|     \/               \/    \/      \/        
     """.stripMargin)))
    logger <- consoleLogger[Task](
      formatter = Formatter.colorful,
      minLevel = Level.Debug
    ).withAsync()
    _ <- Resource.liftF(
      logger.info(s"Starting ${BuildInfo.name}-${BuildInfo.version}")
    )
    db <- SlickResource("myapp.database")
    _ <- Resource.liftF(for {
      config <- JdbcDatabaseConfig.loadFromGlobal("myapp.database")
      _ <- DBMigrations.migrate(config)
    } yield ())
    _ <- Resource.liftF(
      Task.deferAction(implicit s =>
        new Server(db, profile, logger).stream.compile.drain
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
