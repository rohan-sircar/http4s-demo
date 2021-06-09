package wow.doge.http4sdemo.server

import scala.jdk.CollectionConverters._

import cats.effect.Blocker
import cats.effect.ContextShift
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
import pureconfig.module.catseffect.syntax._

/** @author Alexandru Nedelcu
  */

final case class JdbcDatabaseConfig(
    url: String,
    driver: String,
    user: Option[String],
    password: Option[String],
    migrationsTable: String,
    migrationsLocations: List[String]
)

object JdbcDatabaseConfig {
  def loadFromGlobal[F[_]: Sync: ContextShift](
      configNamespace: String,
      blocker: Blocker
  ): F[JdbcDatabaseConfig] =
    Sync[F].defer {
      val config = ConfigFactory.load()
      load(config.getConfig(configNamespace), blocker)
    }

  // Integration with PureConfig
  implicit val configConvert: ConfigConvert[JdbcDatabaseConfig] =
    deriveConvert

  def load[F[_]: Sync: ContextShift](
      config: Config,
      blocker: Blocker
  ): F[JdbcDatabaseConfig] =
    ConfigSource.fromConfig(config).loadF[F, JdbcDatabaseConfig](blocker)

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
