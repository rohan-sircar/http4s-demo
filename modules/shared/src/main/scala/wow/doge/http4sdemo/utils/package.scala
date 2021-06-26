package wow.doge.http4sdemo

import cats.syntax.all._
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.ParsingFailure
import io.odin.Logger
import io.odin.meta.Position
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.implicits._

package object utils {
  def observableFromByteStreamA[A: Decoder](stream: fs2.Stream[Task, Byte]) =
    stream.chunks
      .through(io.circe.fs2.byteArrayParserC)
      .through(io.circe.fs2.decoder)
      .adaptError {
        case e: ParsingFailure =>
          AppError.BadInput(e.getMessage)
        case e: DecodingFailure =>
          AppError.BadInput(e.getMessage)
      }
      .toObsU

  def infoSpan[E, A](
      fa: IO[E, A]
  )(implicit logger: Logger[Task], position: Position) =
    logger
      .infoU("Span - Begin")
      .bracket(_ => fa)(_ => logger.infoU("Span - End"))

}
