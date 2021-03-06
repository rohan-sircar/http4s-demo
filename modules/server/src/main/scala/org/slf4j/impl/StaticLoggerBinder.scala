package org.slf4j.impl

import scala.collection.immutable.ArraySeq

import cats.effect.Clock
import cats.effect.ContextShift
import cats.effect.Effect
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Timer
import cats.syntax.all._
import io.odin.Logger
import io.odin.slf4j.OdinLoggerBinder
import monix.execution.Scheduler
import pureconfig.ConfigSource
import wow.doge.http4sdemo.server.AppLogger
import wow.doge.http4sdemo.server.LogstashLogger
import wow.doge.http4sdemo.server.concurrent.Schedulers
import wow.doge.http4sdemo.server.config.LoggerConfig
import wow.doge.http4sdemo.server.config.LoggerRoutes
import wow.doge.http4sdemo.server.config.LogstashConfig

//effect type should be specified inbefore
//log line will be recorded right after the call with no suspension
class StaticLoggerBinder extends OdinLoggerBinder[IO] {

  implicit val s: Scheduler = Schedulers.default.io.value
  implicit val timer: Timer[IO] = IO.timer(s)
  implicit val clock: Clock[IO] = timer.clock
  implicit val cs: ContextShift[IO] = IO.contextShift(s)
  implicit val F: Effect[IO] = IO.ioEffect

  val isTestEnv = sys.env.get("PROJECT_ENV").map(_ === "test").getOrElse(false)

  val loggerConfig =
    if (isTestEnv)
      ConfigSource.default
        .at("http4s-demo.test.logger")
        .loadOrThrow[LoggerConfig]
    else
      ConfigSource.default
        .at("http4s-demo.logger")
        .loadOrThrow[LoggerConfig]

  val logstashConfig = ConfigSource.default
    .at("http4s-demo.logstash")
    .loadOrThrow[LogstashConfig]

  val logstashLogger =
    if (!isTestEnv)
      LogstashLogger[IO](
        loggerConfig,
        logstashConfig,
        Schedulers.default.io.blocker
      )
    else Resource.pure(Logger.noop[IO])

  val (defaultConsoleLogger, release1) =
    (AppLogger[IO](loggerConfig) |+| logstashLogger).allocated.unsafeRunSync()

  ArraySeq(release1).foreach(r => sys.addShutdownHook(r.unsafeRunSync()))

  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
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
      r.filter { case route -> _ =>
        packageName.startsWith(route)
      }.size > 0
    }

  }

  val loggers = routing(loggerConfig.routes)

}

object StaticLoggerBinder extends StaticLoggerBinder {

  var REQUESTED_API_VERSION: String = "1.7"

  def getSingleton: StaticLoggerBinder = this

}
