package wow.doge.http4sdemo.server

import scala.concurrent.duration.MILLISECONDS

import cats.effect.ExitCode
import cats.effect.Resource
import com.codahale.metrics.SharedMetricRegistries
import com.typesafe.config.ConfigFactory
import monix.bio.BIOApp
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.execution.Scheduler
import pureconfig.ConfigSource
import pureconfig.module.catseffect2.syntax._
import wow.doge.http4sdemo.BuildInfo
import wow.doge.http4sdemo.server.concurrent.Schedulers
import wow.doge.http4sdemo.server.config.AppConfig
import wow.doge.http4sdemo.server.utils.S3ClientResource

object Main extends BIOApp {
  val profile = wow.doge.http4sdemo.server.ExtendedPgProfile
  val schedulers = Schedulers.default

  override protected def scheduler: Scheduler = schedulers.async.value

  val program = for {
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
    logger <- AppLogger.routed[Task](appConfig.logger)
    _ <- Resource.eval(
      logger.info(s"Starting ${BuildInfo.name}-${BuildInfo.version}")
    )
    db <- SlickResource("http4s-demo.database", Some(rootConfig), schedulers.io)
    s3 <- S3ClientResource("http://192.168.1.69:4343")(logger)
    _ <- Resource.eval((for {
      config <- JdbcDatabaseConfig.load(
        rootConfig.getConfig("http4s-demo.database"),
        schedulers.io.blocker
      )
      _ <- DBMigrations.migrate(config)
    } yield ()).executeOn(schedulers.io.value))
    _ <- Resource.eval(logger.debug(s"Routes = ${appConfig.logger.routes}"))
    registry <- Resource.eval(
      Task(SharedMetricRegistries.getOrCreate("default"))
    )
    appRoutes <- Resource.eval(
      IO.pure(new AppRoutes(db, registry, s3, appConfig)(logger))
    )
    _ <- new Server(schedulers, appRoutes, appConfig.http)(logger).resource
  } yield ()

  def run(args: List[String]) = {
    program
      .use(_ => Task.never)
      .onErrorHandleWith(ex => UIO(ex.printStackTrace()))
      .as(ExitCode.Success)
  }
}
