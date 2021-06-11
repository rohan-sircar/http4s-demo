package wow.doge.http4sdemo.server.routes

import scala.util.Random

import cats.syntax.all._
import io.circe.syntax._
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Observable
import org.http4s.circe.streamJsonArrayEncoder
import wow.doge.http4sdemo.endpoints.MessageEndpoints
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.StreamEvent
import wow.doge.http4sdemo.models.StreamInputEvent
import wow.doge.http4sdemo.server.RedisSubject
import wow.doge.http4sdemo.utils.infoSpan
import wow.doge.http4sdemo.utils.observableFromByteStreamA

final class MessageRoutes(messageSubject: RedisSubject)(
    val logger: Logger[Task]
) extends ServerInterpreter {

  def handleSubscription(implicit
      logger: Logger[Task]
  ): UIO[fs2.Stream[Task, StreamEvent]] = infoSpan {
    for {
      _ <- IO.unit
      eventStream = (fs2.Stream(StreamEvent.Ack(0)) ++
        messageSubject.rx)
        // .debug()
        .onFinalize(logger.debug("Stopping"))
    } yield eventStream
  }

  val subscriptionRoute = toRoutes(
    MessageEndpoints.subscribeEndpoint
      .serverLogicPart(enrichLogger)
      .andThenRecoverErrors { case (logger, _) =>
        handleSubscription(logger)
          .map(o => streamJsonArrayEncoder[Task].toEntity(o.map(_.asJson)).body)
      }
  )

  def handlePublish(inputEvents: Observable[StreamInputEvent])(implicit
      logger: Logger[Task]
  ): UIO[Unit] = infoSpan {
    for {
      eventsStream <- inputEvents
        .mapEval(event =>
          UIO(Random.between(1, 10)).map(event.toOutputEvent).toTask
        )
        .toStreamIO
        .hideErrors
      _ <- eventsStream.through(messageSubject.tx).compile.drain.hideErrors
    } yield ()
  }

  val publishRoutes = toRoutes(
    MessageEndpoints.publishEndpoint
      .serverLogicPart(enrichLogger)
      .andThenRecoverErrors { case (logger, byteStream) =>
        for {
          events <- observableFromByteStreamA[StreamInputEvent](byteStream)
          _ <- handlePublish(events)(logger)
        } yield ()
      }
  )

  val routes = publishRoutes <+> subscriptionRoute
}
