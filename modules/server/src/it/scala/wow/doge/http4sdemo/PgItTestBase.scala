package wow.doge.http4sdemo

import cats.effect.Resource
import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import com.typesafe.config.ConfigFactory
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import org.testcontainers.utility.DockerImageName
import slick.jdbc.JdbcBackend
import wow.doge.http4sdemo.MonixBioSuite
import wow.doge.http4sdemo.server.DBMigrations
import wow.doge.http4sdemo.server.JdbcDatabaseConfig

trait PgItTestBase
    extends MonixBioSuite
    with TestContainerForAll
    with PgItTestOps {
  val databaseName = "testcontainer-scala"
  val username = "scala"
  val password = "scala"

  override val containerDef: ContainerDef = pgContainerDef

  def withContainersIO[A](f: PostgreSQLContainer => Task[A]): Task[A] = {
    withContainers {
      case c: PostgreSQLContainer => f(c)
      case c                      => IO.terminate(new Exception(s"Unknown container: ${c.toString}"))
    }
  }

}

trait PgItTestOps {

  def databaseName: String
  def username: String
  def password: String

  lazy val pgContainerDef = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:12-alpine"),
    databaseName = databaseName,
    username = username,
    password = password
  )

  def config(url: String) = ConfigFactory.parseString(s"""|
                               |testDatabase = {
                               |    url = "$url"
                               |    driver = org.postgresql.Driver
                               |    user = $username
                               |    password = $password
                               |
                               |    numThreads = 1
                               |
                               |    queueSize = 1000000
                               |
                               |    maxThreads = 1
                               |
                               |    maxConnections = 1
                               |
                               |}""".stripMargin)

  def dbResource(url: String) = Resource.make(
    Task(
      JdbcBackend.Database.forConfig("testDatabase", config(url))
    )
  )(db => UIO(db.close()))

  def withDb[T](url: String)(f: JdbcBackend.DatabaseDef => Task[T]) =
    dbResource(url).use(f)

  def createSchema(container: PostgreSQLContainer) = {
    val config = JdbcDatabaseConfig(
      container.jdbcUrl,
      "org.postgresql.Driver",
      Some(username),
      Some(password),
      "flyway_schema_history",
      List("classpath:db/migration/default")
    )
    DBMigrations.migrate[Task](config).void
  }
}
