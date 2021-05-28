package org.slf4j.impl

import scala.collection.immutable.ArraySeq

import cats.effect.Clock
import cats.effect.ContextShift
import cats.effect.Effect
import cats.effect.IO
import cats.effect.Timer
import cats.syntax.eq._
import io.odin.Logger
import io.odin.consoleLogger
import io.odin.slf4j.OdinLoggerBinder
import monix.execution.Scheduler
import pureconfig.ConfigSource
import wow.doge.http4sdemo.AppLogger
import wow.doge.http4sdemo.schedulers.Schedulers
import wow.doge.http4sdemo.server.config.AppConfig
import io.odin.Level
import io.odin.formatter.Formatter
import wow.doge.http4sdemo.server.config.LoggerRoutes

//effect type should be specified inbefore
//log line will be recorded right after the call with no suspension
class StaticLoggerBinder extends OdinLoggerBinder[IO] {

  val s: Scheduler = Schedulers.default.async.value
  implicit val timer: Timer[IO] = IO.timer(s)
  implicit val clock: Clock[IO] = timer.clock
  implicit val cs: ContextShift[IO] = IO.contextShift(s)
  implicit val F: Effect[IO] = IO.ioEffect

  val config = ConfigSource.default.at("http4s-demo").loadOrThrow[AppConfig]

  val isTestEnv = sys.env.get("PROJECT_ENV").map(_ === "test").getOrElse(false)

  val (defaultConsoleLogger, release1) =
    AppLogger[IO](config.logger).allocated.unsafeRunSync()

  ArraySeq(release1).foreach(r => sys.addShutdownHook(r.unsafeRunSync()))

  def routing(routes: LoggerRoutes) = new PartialFunction[String, Logger[IO]] {
    val r = routes.value
    override def apply(packageName: String): Logger[IO] = {
      r.view
        .filter { case route -> _ =>
          packageName.startsWith(route)
        }
        .map { case _ -> level =>
          defaultConsoleLogger.withMinimalLevel(level.toOdin)
        }
        .head
      // .headOption
      // .getOrElse(Logger.noop)
    }

    override def isDefinedAt(packageName: String): Boolean = {
      r
        .filter { case route -> _ =>
          packageName.startsWith(route)
        }
        .toList
        .length > 0
    }

  }

  val pf =
    if (isTestEnv) routing(config.logger.testRoutes)
    else routing(config.logger.routes)

  val loggers = pf

}

object StaticLoggerBinder extends StaticLoggerBinder {

  var REQUESTED_API_VERSION: String = "1.7"

  def getSingleton: StaticLoggerBinder = this

}
