package wow.doge.http4sdemo.server.utils

import cats.data.NonEmptyList
import cats.syntax.all._
import enumeratum.EnumEntry
import enumeratum._
import fs2.Chunk
import monix.bio.Task
import monix.reactive.Observable
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.implicits._

sealed abstract class ImageFormat(
    val header: fs2.Chunk[Byte],
    val extensions: NonEmptyList[String]
) extends EnumEntry

object ImageFormat extends Enum[ImageFormat] {
  val values = findValues
  case object Png extends ImageFormat(ImageHeaders.Png, NonEmptyList.one("png"))
  case object Jpeg
      extends ImageFormat(ImageHeaders.Jpeg, NonEmptyList.of("jpg", "jpeg"))

  val formats = values.flatMap(_.extensions.toList)
}

final case class ImageStream private (
    format: ImageFormat,
    stream: fs2.Stream[Task, Byte],
    obs: Observable[Chunk[Byte]]
)

object ImageStream {
  def parseFormatFromStream(
      data: fs2.Stream[Task, Byte],
      format: ImageFormat
  ) = for {
    _ <- data
      .take(format.header.size.toLong)
      .chunkAll
      .evalMap(header =>
        if (header === format.header) Task.unit
        else
          Task.raiseError(
            AppError2.BadInput(
              "Image format did not match any of " +
                s"${format.extensions.mkString_("[", ",", "]")}"
            )
          )
      )
      .compile
      .drain
      .mapErrorPartial { case e: AppError2 => e }
    obs <- data.chunks.toObsU
  } yield ImageStream(format, data, obs)

  def parseFormatFromObs(
      data: Observable[Chunk[Byte]],
      format: ImageFormat
  ) =
    for {
      _ <- data
        .take(1)
        .map(a => a.take(format.header.size))
        .mapEval(header =>
          if (header === format.header) Task.unit.toTask
          else
            Task
              .raiseError(
                AppError2.BadInput(
                  "Image format did not match any of " +
                    s"${format.extensions.mkString_("[", ",", "]")}"
                )
              )
              .toTask
        )
        .completedL
        .toIO
        .mapErrorPartial { case e: AppError2 => e }
      stream <- data.toStreamIO.hideErrors
    } yield ImageStream(format, stream.flatMap(c => fs2.Stream.chunk(c)), data)

  def parse(data: fs2.Stream[Task, Byte]) =
    parseFormatFromStream(data, ImageFormat.Png)
      .onErrorFallbackTo(parseFormatFromStream(data, ImageFormat.Jpeg))
      .mapErrorPartial {
        case ex: AppError2.BadInput
            if ex.getMessage.startsWith("Image format did not match") =>
          AppError2.BadInput(
            "Failed to parse image format. None of the supported formats matched. " +
              s"Supported formats are: ${ImageFormat.formats}"
          )
      }

  def parse(data: Observable[Chunk[Byte]]) =
    parseFormatFromObs(data, ImageFormat.Png)
      .onErrorFallbackTo(parseFormatFromObs(data, ImageFormat.Jpeg))
      .mapErrorPartial {
        case ex: AppError2.BadInput
            if ex.getMessage.startsWith("Image format did not match") =>
          AppError2.BadInput(
            "Failed to parse image format. None of the supported formats matched. " +
              s"Supported formats are: ${ImageFormat.formats}"
          )
      }

}

object ImageHeaders {
  val Png = fs2
    .Chunk(0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a)
    .map(_.toByte)

  val Jpeg = fs2
    .Chunk(0xff, 0xd8, 0xff)
    .map(_.toByte)
}
