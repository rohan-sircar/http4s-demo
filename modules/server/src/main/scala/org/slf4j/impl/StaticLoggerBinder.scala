package org.slf4j.impl

import scala.collection.immutable.ArraySeq

import _root_.monix.execution.Scheduler
import cats.effect.Clock
import cats.effect.ContextShift
import cats.effect.Effect
import cats.effect.IO
import cats.effect.Timer
import io.odin._
import io.odin.slf4j.OdinLoggerBinder
import pureconfig.ConfigSource
import wow.doge.http4sdemo.AppLogger
import wow.doge.http4sdemo.schedulers.Schedulers
import wow.doge.http4sdemo.server.config.AppConfig

//effect type should be specified inbefore
//log line will be recorded right after the call with no suspension
class StaticLoggerBinder extends OdinLoggerBinder[IO] {

  val s: Scheduler = Schedulers.default.async.value
  implicit val timer: Timer[IO] = IO.timer(s)
  implicit val clock: Clock[IO] = timer.clock
  implicit val cs: ContextShift[IO] = IO.contextShift(s)
  implicit val F: Effect[IO] = IO.ioEffect

  val config = ConfigSource.default.at("http4s-demo").loadOrThrow[AppConfig]

  val (defaultConsoleLogger, release1) =
    AppLogger[IO](config.logger).allocated.unsafeRunSync()

  ArraySeq(release1).foreach(r => sys.addShutdownHook(r.unsafeRunSync()))

  val loggers = new PartialFunction[String, Logger[IO]] {
    val routes = config.logger.routes.value
    override def apply(packageName: String): Logger[IO] = {
      routes.view
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
      routes
        .filter { case route -> _ =>
          packageName.startsWith(route)
        }
        .toList
        .length > 0
    }

  }

}

object StaticLoggerBinder extends StaticLoggerBinder {

  var REQUESTED_API_VERSION: String = "1.7"

  def getSingleton: StaticLoggerBinder = this

}
