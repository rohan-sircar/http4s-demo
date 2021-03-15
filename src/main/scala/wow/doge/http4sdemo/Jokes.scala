package wow.doge.http4sdemo

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import monix.bio.Task
import org.http4s.Method._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._

sealed trait Jokes[F[_]] {
  def get: F[Jokes.Joke]
}

object Jokes {
  def apply[F[_]](implicit ev: Jokes[F]): Jokes[F] = ev

  final case class Joke(joke: String)
  object Joke {
    implicit val jokeDecoder: Decoder[Joke] = deriveDecoder[Joke]
    implicit def jokeEntityDecoder[F[_]: Sync]: EntityDecoder[F, Joke] =
      jsonOf
    implicit val jokeEncoder: Encoder[Joke] = deriveEncoder[Joke]
    implicit def jokeEntityEncoder[F[_]: Applicative]: EntityEncoder[F, Joke] =
      jsonEncoderOf
  }

  final case class JokeError(e: Throwable) extends RuntimeException

  def impl(C: Client[Task]): Jokes[Task] = new Jokes[Task] {
    val dsl = new Http4sClientDsl[Task] {}
    import dsl._
    def get: Task[Jokes.Joke] = {
      C.expect[Joke](GET(uri"https://icanhazdadjoke.com/"))
        .adaptError { case t =>
          JokeError(t)
        } // Prevent Client Json Decoding Failure Leaking
    }
  }

}
