package wow.doge.http4sdemo.server.utils

final class IO[A] private (inner: () => A) { self =>
//   def map[B](f: A => B): IO[B] = new IO(() => f(inner()))
  def map[B](f: A => B): IO[B] = {
    // val a = inner()
    // val b = IO.pure(f(a))
    // b
    self.flatMap(a => IO(f(a)))
  }

  def flatMap[B](f: A => IO[B]): IO[B] = f(inner())

  def run() = inner()

}

object IO {
  def apply[A](v: => A) = new IO(() => v)

  def pure[A](v: A) = new IO(() => v)

}
