package wow.doge.http4sdemo.server

import dev.profunktor.redis4cats.pubsub.PubSubCommands
import monix.bio.Task
import wow.doge.http4sdemo.models.StreamEvent

package object types {
  type RedisStreamEventPs =
    PubSubCommands[fs2.Stream[Task, *], String, StreamEvent]
}
