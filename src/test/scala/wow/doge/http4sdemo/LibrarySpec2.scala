package wow.doge.http4sdemo

import com.dimafeng.testcontainers.PostgreSQLContainer
import monix.bio.IO
import monix.bio.UIO
import wow.doge.http4sdemo.services.LibraryDbio
import wow.doge.http4sdemo.services.LibraryServiceImpl

class LibrarySpec2 extends DatabaseIntegrationTestBase {

  override def afterContainersStart(containers: Containers): Unit = {
    createSchema(containers)
  }

  test("blah") {
    withContainers {
      case postgresContainer: PostgreSQLContainer =>
        val io =
          withDb(postgresContainer.jdbcUrl)(db =>
            for {
              // _ <- db.runL(Tables.schema.create)
              _ <- UIO.unit
              service = new LibraryServiceImpl(
                profile,
                new LibraryDbio(profile),
                db
              )
              _ <- service
                .getBookById(1)
                .hideErrors
                .flatMap(r => UIO(println(r)))
            } yield ()
          )
        io
      case other =>
        IO.terminate(new Exception(s"Invalid container ${other.toString}"))
    }
  }

//   override val container: PostgreSQLContainer = PostgreSQLContainer()

//   "PostgreSQL container" should "be started" in {
//     Class.forName(container.driverClassName)
//     val connection = DriverManager.getConnection(
//       container.jdbcUrl,
//       container.username,
//       container.password
//     )
//     //   ...
//   }
}
