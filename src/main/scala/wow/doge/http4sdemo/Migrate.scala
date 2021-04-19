package wow.doge.http4sdemo

import scala.jdk.CollectionConverters._

import cats.effect.Sync
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.configuration.FluentConfiguration
import pureconfig.ConfigConvert
import pureconfig.ConfigSource
import pureconfig.generic.semiauto._

final case class JdbcDatabaseConfig(
    url: String,
    driver: String,
    user: Option[String],
    password: Option[String],
    migrationsTable: String,
    migrationsLocations: List[String]
)

object JdbcDatabaseConfig {
  def loadFromGlobal[F[_]: Sync](
      configNamespace: String
  ): F[JdbcDatabaseConfig] =
    Sync[F].suspend {
      val config = ConfigFactory.load()
      load(config.getConfig(configNamespace))
    }

  // Integration with PureConfig
  implicit val configConvert: ConfigConvert[JdbcDatabaseConfig] =
    deriveConvert

  def load[F[_]: Sync](config: Config): F[JdbcDatabaseConfig] =
    Sync[F].delay {
      ConfigSource.fromConfig(config).loadOrThrow[JdbcDatabaseConfig]
    }

}

object DBMigrations extends LazyLogging {

  def migrate[F[_]: Sync](config: JdbcDatabaseConfig): F[Int] =
    Sync[F].delay {
      logger.info(
        "Running migrations from locations: " +
          config.migrationsLocations.mkString(", ")
      )
      val count = unsafeMigrate(config)
      logger.info(s"Executed $count migrations")
      count
    }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def unsafeMigrate(config: JdbcDatabaseConfig): Int = {
    val m: FluentConfiguration = Flyway.configure
      .dataSource(
        config.url,
        config.user.orNull,
        config.password.orNull
      )
      .group(true)
      .outOfOrder(false)
      .table(config.migrationsTable)
      .locations(
        config.migrationsLocations
          .map(new Location(_))
          .toList: _*
      )
      .baselineOnMigrate(true)

    logValidationErrorsIfAny(m)
    m.load()
      .migrate()
      .migrationsExecuted
  }

  private def logValidationErrorsIfAny(m: FluentConfiguration): Unit = {
    val validated = m
      .ignorePendingMigrations(true)
      .load()
      .validateWithResult()

    if (!validated.validationSuccessful)
      for (error <- validated.invalidMigrations.asScala)
        logger.warn(s"""
          |Failed validation:
          |  - version: ${error.version}
          |  - path: ${error.filepath}
          |  - description: ${error.description}
          |  - errorCode: ${error.errorDetails.errorCode}
          |  - errorMessage: ${error.errorDetails.errorMessage}
        """.stripMargin.strip)
  }
}
