package wow.doge.http4sdemo.server

import cats.effect.Resource
import com.typesafe.config.Config
import monix.bio.Task
import slick.jdbc.JdbcBackend.Database
import wow.doge.http4sdemo.server.concurrent.Schedulers

@SuppressWarnings(Array("org.wartremover.warts.Null"))
object SlickResource {
  def apply(
      confPath: String,
      config: Option[Config],
      sched: Schedulers.IoScheduler
  ) =
    Resource.make(
      Task(Database.forConfig(path = confPath, config = config.orNull))
        .executeOn(sched.value)
    )(db => Task(db.close()))
}
