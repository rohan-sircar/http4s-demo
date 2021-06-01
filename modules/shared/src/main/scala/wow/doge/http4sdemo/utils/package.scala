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
package utils {

  import sttp.tapir.Mapping
  final case class ReqContext(
      reqId: String,
      reqUri: String,
      reqParams: String
  )
  object ReqContext {
    // implicit val tapirMapping = Mapping.from[(String), ReqContext] {
    //   case reqId => ReqContext(reqId)
    // } { _.requestId }

    // implicit val tapirMapping2 = Mapping.fromDecode[(String), ReqContext] {
    //   case reqId => sttp.tapir.DecodeResult.Value(ReqContext(reqId))
    // } { _.requestId }

    val empty = ReqContext("null", "null", "null")

    implicit val tapirMapping = Mapping.fromDecode[
      (Option[String], Option[String], Option[String]),
      ReqContext
    ] { case (reqId, reqUri, reqParams) =>
      sttp.tapir.DecodeResult.Value(
        ReqContext(
          reqId.getOrElse("null"),
          reqUri.getOrElse("null"),
          reqParams.getOrElse("null")
        )
      )
    } { ctx => (Some(ctx.reqId), Some(ctx.reqUri), Some(ctx.reqParams)) }
  }
}
