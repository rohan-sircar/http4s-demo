package wow.doge.http4sdemo.utils

import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.catnap.MVar

/** An Async Mutex
  *
  * Copied from monix documentation for MVar
  */
final class MLock private (mvar: MVar[Task, Unit]) {
  private def acquire[E]: IO[E, Unit] =
    mvar.take.hideErrors

  private def release[E]: IO[E, Unit] =
    mvar.put(()).hideErrors

  def greenLight[E, A](fa: IO[E, A]): IO[E, A] =
    for {
      _ <- acquire
      a <- fa.doOnCancel(release)
      _ <- release
    } yield a
}

object MLock {

  /** Builder. */
  def apply(): UIO[MLock] =
    MVar[Task].of(()).map(v => new MLock(v)).hideErrors
}
