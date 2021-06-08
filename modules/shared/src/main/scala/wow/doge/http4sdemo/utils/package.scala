package wow.doge.http4sdemo

import io.circe.Decoder
import monix.bio.Task
import monix.bio.UIO
import wow.doge.http4sdemo.implicits._

package object utils {
  def observableFromByteStreamA[A: Decoder](stream: fs2.Stream[Task, Byte]) =
    UIO.deferAction { implicit s =>
      UIO(
        stream.chunks
          .through(io.circe.fs2.byteArrayParserC)
          .through(io.circe.fs2.decoder)
          .toObs
      )
    }
}
