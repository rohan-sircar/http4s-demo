package wow.doge.http4sdemo

import scala.collection.compat.immutable.ArraySeq

import eu.timepit.refined.auto._
import fs2.Chunk
import monix.bio.IO
import monix.bio.UIO
import monix.reactive.Observable
import wow.doge.http4sdemo.server.utils.ImageFormat
import wow.doge.http4sdemo.server.utils.ImageStream
import wow.doge.http4sdemo.server.utils.checkImageType

final class ImageStreamParseSpec extends MonixBioSuite {
  test("check image type for byte observable should succeed") {
    for {
      _ <- IO.unit
      data = Observable.pure(
        ArraySeq.unsafeWrapArray(
          os.read.bytes(os.resource / "images" / "JME.png")
        )
      )
      _ <- checkImageType(data)
    } yield ()
  }

  test("parse png image stream should succeed") {
    for {
      data <- UIO(
        fs2.Stream.chunk(
          Chunk.array(
            os.read.bytes(os.resource / "images" / "JME.png")
          )
        )
      ).executeOn(schedulers.io.value)
      _ <- ImageStream
        .parse(data)
        .map(_.format)
        .assertEquals(ImageFormat.Png)
    } yield ()
  }

  test("parse jpeg image stream should succeed") {
    for {
      data <- UIO(
        fs2.Stream.chunk(
          Chunk.array(
            os.read.bytes(os.resource / "images" / "JME.jpeg")
          )
        )
      ).executeOn(schedulers.io.value)
      _ <- ImageStream
        .parse(data)
        .map(_.format)
        .assertEquals(ImageFormat.Jpeg)
    } yield ()
  }

  test("parse image stream should fail for non-image binary data") {
    for {
      data <- UIO(
        fs2.Stream.chunk(
          Chunk.array(
            os.read.bytes(os.resource / "images" / "hello.txt")
          )
        )
      ).executeOn(schedulers.io.value)
      _ <- ImageStream
        .parse(data)
        .map(_.format)
        .mapError(
          _.message.startsWith(
            "Failed to parse image format. None of the supported formats matched."
          )
        )
        .attempt
        .assertEquals(Left(true))
    } yield ()
  }
}
