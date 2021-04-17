package wow.doge.http4sdemo

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import com.typesafe.config.ConfigFactory
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.execution.Scheduler
import org.testcontainers.utility.DockerImageName
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile

trait DatabaseIntegrationTestBase
    extends MonixBioSuite
    with TestContainerForAll {
  def databaseName = "testcontainer-scala"
  def username = "scala"
  def password = "scala"

  override val containerDef: ContainerDef = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:12-alpine"),
    databaseName = databaseName,
    username = username,
    password = password
  )

  lazy val profile = PostgresProfile

  def config(url: String) = ConfigFactory.parseString(s"""|
                               |testDatabase = {
                               |    url = "$url"
                               |    driver = org.postgresql.Driver
                               |    user = $username
                               |    password = $password
                               |
                               |    numThreads = 2
                               |
                               |    queueSize = 10
                               |
                               |    maxThreads = 2
                               |
                               |    maxConnections = 2
                               |
                                }""".stripMargin)

  def withDb[T](url: String)(f: JdbcBackend.DatabaseDef => Task[T]) = Task(
    // JdbcBackend.Database.forURL(
    //   url,
    //   //   user = username,
    //   //   password = password,
    //   //   driver = "org.postgresql.Driver",
    //   prop = Map(
    //     "driver" -> "org.postgresql.Driver",
    //     "user" -> username,
    //     "password" -> password,
    //     "numThreads" -> "16",
    //     "maxThreads" -> "36",
    //     "queueSize" -> "10",
    //     "maxConnections" -> "36"
    //   )
    // )
    JdbcBackend.Database.forConfig("testDatabase", config(url))
  ).bracket(f)(db => UIO(db.close()))

  def createSchema(containers: Containers) = {
    implicit val s = Scheduler.global
    containers match {
      case container: PostgreSQLContainer =>
        val config = JdbcDatabaseConfig(
          container.jdbcUrl,
          "org.postgresql.Driver",
          Some(username),
          Some(password),
          "flyway_schema_history",
          List("classpath:db/migration/default")
        )
        // (UIO(println("creating db")) >> dbBracket(container.jdbcUrl)(
        //   // _.runL(Tables.schema.create)
        //   _ => DBMigrations.migrate[Task](config)
        // ))
        DBMigrations.migrate[Task](config).runSyncUnsafe(munitTimeout)
      case _ => ()
    }
  }

  // val fixture = ResourceFixture(
  //   Resource.make(
  //     Task(
  //       JdbcBackend.Database.forURL(
  //         "jdbc:postgresql://localhost:49162/testcontainer-scala?",
  //         user = username,
  //         password = password,
  //         driver = "org.postgresql.Driver"
  //       )
  //     )
  //   )(db => Task(db.close()))
  // )

  def withContainersIO[A](pf: PartialFunction[Containers, Task[A]]): Task[A] = {
    withContainers { containers =>
      pf.applyOrElse(
        containers,
        (c: Containers) =>
          IO.terminate(new Exception(s"Unknown container: ${c.toString}"))
      )
    }
  }

}
