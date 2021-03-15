package wow.doge.http4sdemo

import cats.effect.Resource
import monix.bio.Task
import slick.jdbc.JdbcBackend.Database

object SlickResource {
  def apply(confPath: String) =
    Resource.make(Task(Database.forConfig(confPath)))(db =>
      Task(db.source.close()) >> Task(db.close())
    )
}
