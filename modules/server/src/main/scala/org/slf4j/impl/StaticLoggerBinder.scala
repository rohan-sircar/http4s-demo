package org.slf4j.impl

import scala.collection.immutable.ArraySeq
import scala.concurrent.duration._

import _root_.monix.execution.Scheduler
import cats.effect.Clock
import cats.effect.ContextShift
import cats.effect.Effect
import cats.effect.IO
import cats.effect.Timer
import io.odin._
import io.odin.formatter.Formatter
import io.odin.slf4j.OdinLoggerBinder
import io.odin.syntax._
import wow.doge.http4sdemo.schedulers.Schedulers

//effect type should be specified inbefore
//log line will be recorded right after the call with no suspension
class StaticLoggerBinder extends OdinLoggerBinder[IO] {

  val s: Scheduler = Schedulers.default.async.value
  implicit val timer: Timer[IO] = IO.timer(s)
  implicit val clock: Clock[IO] = timer.clock
  implicit val cs: ContextShift[IO] = IO.contextShift(s)
  implicit val F: Effect[IO] = IO.ioEffect

  val (defaultConsoleLogger, release1) =
    consoleLogger[IO](minLevel = Level.Debug, formatter = Formatter.colorful)
      .withAsync(timeWindow = 1.milliseconds, maxBufferSize = Some(2000))
      .allocated
      .unsafeRunSync()

  ArraySeq(release1).foreach(r => sys.addShutdownHook(r.unsafeRunSync()))

  val loggers: PartialFunction[String, Logger[IO]] = {
    // case s
    //     if s.startsWith("slick.jdbc.JdbcBackend.statement") |
    //       s.startsWith("slick.jdbc.JdbcBackend.statementAndParameter") |
    //       s.startsWith("slick.jdbc.JdbcBackend.parameter") |
    //       s.startsWith("slick.jdbc.StatementInvoker.result")
    //     // s.startsWith("slick")
    //     =>
    //   defaultConsoleLogger.withMinimalLevel(Level.Debug)

    case _ => //if wildcard case isn't provided, default logger is no-op
      // defaultConsoleLogger.withMinimalLevel(Level.Info)
      Logger.noop[IO]
  }
}

object StaticLoggerBinder extends StaticLoggerBinder {

  var REQUESTED_API_VERSION: String = "1.7"

  def getSingleton: StaticLoggerBinder = this

}
