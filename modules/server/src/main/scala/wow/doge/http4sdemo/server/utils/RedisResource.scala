package wow.doge.http4sdemo.server.utils

import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.log4cats._
import io.odin.Logger
import monix.bio.Task
import org.typelevel.log4cats
import wow.doge.http4sdemo.server.config.RedisUrl
import wow.doge.http4sdemo.server.utils.StructuredOdinLogger2
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import io.circe.parser.{decode => jsonDecode}
import io.circe.syntax._
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.pubsub.PubSub
import cats.effect.Resource
import wow.doge.http4sdemo.models.StreamEvent

object RedisResource {

  val eventSplitEpi: SplitEpi[String, StreamEvent] =
    SplitEpi[String, StreamEvent](
      str =>
        jsonDecode[StreamEvent](str) match {
          case Left(value)  => StreamEvent.Unknown(value.getMessage)
          case Right(value) => value
        },
      _.asJson.noSpaces
    )

  val eventsCodec: RedisCodec[String, StreamEvent] =
    Codecs.derive(RedisCodec.Utf8, eventSplitEpi)

  def apply(url: RedisUrl, logger: Logger[Task]) = {
    implicit val l: log4cats.Logger[Task] =
      new StructuredOdinLogger2(logger, "redisClient")
    for {
      client <- RedisClient[Task].from(url.inner.value)
      ps <- Resource.suspend(
        Task.deferAction(implicit s =>
          Task.pure(
            PubSub.mkPubSubConnection[Task, String, StreamEvent](
              client,
              eventsCodec
            )
          )
        )
      )
      cmd <- Redis[Task].fromClient(client, RedisCodec.Utf8)
    } yield ps -> cmd
  }
}
