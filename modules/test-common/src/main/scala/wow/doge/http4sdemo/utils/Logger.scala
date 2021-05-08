package wow.doge.http4sdemo.utils

import io.odin.meta.Render
import io.odin.meta.Position
import monix.bio.UIO
import monix.bio.Task
import io.odin
import cats.effect.concurrent.Ref

//format: off
trait Logger {
  def debugU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit]
  def infoU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit]
  def traceU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit]
  def warnU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit]
  def errorU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit]
  def debug[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit]
  def info[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit]
  def trace[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit]
  def warn[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit]
  def error[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit]
}

final class OdinLogger(logger: odin.Logger[Task]) extends Logger {

  override def debugU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
    logger.debug(msg).hideErrors

  override def infoU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
    logger.info(msg).hideErrors

  override def traceU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
    logger.trace(msg).hideErrors

  override def warnU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
    logger.warn(msg).hideErrors

  override def errorU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
    logger.error(msg).hideErrors

  override def debug[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
    logger.debug(msg)

  override def info[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
    logger.info(msg)

  override def trace[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
    logger.trace(msg)

  override def warn[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
    logger.warn(msg)

  override def error[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
    logger.error(msg)

}

/**
  * A logger that only records log events in the given stack,
  * but doesn't actually print anything
  *
  */
class TracingStubLogger(stack: Ref[Task, List[String]])
    extends wow.doge.http4sdemo.utils.Logger {

  override def debugU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
    stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)
    .hideErrors

  override def infoU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)
    .hideErrors

  override def traceU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)
    .hideErrors

  override def warnU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)
    .hideErrors

  override def errorU[M](msg: => M)(implicit render: Render[M], position: Position): UIO[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)
    .hideErrors

  override def debug[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)

  override def info[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)

  override def trace[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)

  override def warn[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)

  override def error[M](msg: => M)(implicit render: Render[M], position: Position): Task[Unit] = 
     stack.update(lst => s"msg=${render.render(msg)},position=${position.packageName},${position.fileName}" +
        s"${position.enclosureName},${position.line}" :: lst)
}

