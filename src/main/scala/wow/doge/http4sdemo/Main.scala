package wow.doge.http4sdemo

import scala.concurrent.duration.MILLISECONDS

import cats.effect.ExitCode
import cats.effect.Resource
import com.typesafe.config.ConfigFactory
import io.odin.Level
import io.odin.consoleLogger
import io.odin.formatter.Formatter
import io.odin.json.{Formatter => JFormatter}
import io.odin.syntax._
import monix.bio.BIOApp
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.execution.Scheduler
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax._
import wow.doge.http4sdemo.schedulers.Schedulers
import wow.doge.http4sdemo.utils.config.AppConfig
import wow.doge.http4sdemo.utils.config.LoggerFormat.Json
import wow.doge.http4sdemo.utils.config.LoggerFormat.Pretty

object Main extends BIOApp {
  val profile = wow.doge.http4sdemo.profile.ExtendedPgProfile
  val schedulers = Schedulers.default

  override protected def scheduler: Scheduler = schedulers.async.value
  val app = for {
    startTime <- Resource.eval(IO.clock.realTime(MILLISECONDS))
    _ <- Resource.eval(Task(println("""
    |        .__     __    __            _____                      .___                     
    |        |  |___/  |__/  |_______   /  |  |  ______           __| _/____   _____   ____  
    |        |  |  \   __\   __\____ \ /   |  |_/  ___/  ______  / __ |/ __ \ /     \ /  _ \ 
    |        |   Y  \  |  |  | |  |_> >    ^   /\___ \  /_____/ / /_/ \  ___//  Y Y  (  <_> )
    |        |___||_|__|  |__| |   __/\____   |/______|         \_____|\____/\__|_|_/ \____/ 
    |                          |__|        |__|                                        
     """.stripMargin)))
    rootConfig <- Resource.eval(
      Task(ConfigFactory.load()).executeOn(schedulers.io.value)
    )
    appConfig <- Resource.eval(
      ConfigSource
        .fromConfig(rootConfig.getConfig("http4s-demo"))
        .loadF[Task, AppConfig](schedulers.io.blocker)
    )
    logger <- consoleLogger[Task](
      formatter = appConfig.loggerFormat match {
        case Json   => JFormatter.json
        case Pretty => Formatter.colorful
      },
      minLevel = Level.Debug
    ).withAsync()
    _ <- Resource.eval(
      logger.info(s"Starting ${BuildInfo.name}-${BuildInfo.version}")
    )
    db <- SlickResource("http4s-demo.database", Some(rootConfig), schedulers.io)
    _ <- Resource.eval((for {
      config <- JdbcDatabaseConfig.load(
        rootConfig.getConfig("http4s-demo.database"),
        schedulers.io.blocker
      )
      _ <- DBMigrations.migrate(config)
    } yield ()).executeOn(schedulers.io.value))
    _ <- new Server(db, profile, logger, schedulers, appConfig.http).resource
  } yield ()

  def run(args: List[String]) = {
    app
      .use(_ => Task.never)
      .onErrorHandleWith(ex => UIO(ex.printStackTrace()))
      .as(ExitCode.Success)
  }
}
