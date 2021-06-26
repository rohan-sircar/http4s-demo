package wow.doge.http4sdemo.server

import java.nio.charset.StandardCharsets

import scala.concurrent.duration._

import cats.effect.Resource
import cats.syntax.all._
import com.codahale.metrics.MetricRegistry
import dev.profunktor.redis4cats.data
import fs2.Pipe
import io.odin.Logger
import monix.bio.Task
import monix.connect.s3.S3
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.implicits._
import org.http4s.metrics.dropwizard.Dropwizard
import org.http4s.metrics.dropwizard.metricsResponse
import org.http4s.server.middleware.Metrics
import org.http4s.server.middleware.Timeout
import slick.jdbc.JdbcBackend.DatabaseDef
import tsec.mac.jca.HMACSHA256
import wow.doge.http4sdemo.models.StreamEvent
import wow.doge.http4sdemo.server.auth.JwtSigningKey
import wow.doge.http4sdemo.server.config.AppConfig
import wow.doge.http4sdemo.server.config.AuthSessionConfig
import wow.doge.http4sdemo.server.repos.BookImagesRepoImpl
import wow.doge.http4sdemo.server.repos.CredentialsRepo
import wow.doge.http4sdemo.server.repos.InMemoryCredentialsRepo
import wow.doge.http4sdemo.server.repos.RedisCredentialsRepo
import wow.doge.http4sdemo.server.repos.UsersDbio
import wow.doge.http4sdemo.server.repos.UsersRepoImpl
import wow.doge.http4sdemo.server.routes.AccountRoutes
import wow.doge.http4sdemo.server.routes.LibraryRoutes
import wow.doge.http4sdemo.server.routes.LibraryRoutes2
import wow.doge.http4sdemo.server.routes.MessageRoutes
import wow.doge.http4sdemo.server.routes.UserRoutes
import wow.doge.http4sdemo.server.services.AuthServiceImpl
import wow.doge.http4sdemo.server.services.LibraryDbio
import wow.doge.http4sdemo.server.services.LibraryServiceImpl
import wow.doge.http4sdemo.server.services.UserServiceImpl
import wow.doge.http4sdemo.server.types.RedisStreamEventPs
import wow.doge.http4sdemo.server.utils.RedisResource

final class AppRoutes(
    db: DatabaseDef,
    registry: MetricRegistry,
    s3: S3,
    config: AppConfig
)(implicit logger: Logger[Task]) {
  val routes = for {
    _key <- Resource.eval(
      HMACSHA256.buildKey[Task](
        config.auth.secretKey.value.getBytes(StandardCharsets.UTF_8)
      )
    )
    key = JwtSigningKey(_key)
    usersDbio = new UsersDbio
    usersRepo = new UsersRepoImpl(db, usersDbio)
    userService = new UserServiceImpl(usersRepo)
    libraryDbio = new LibraryDbio
    bookImagesRepo <- Resource.eval(
      BookImagesRepoImpl(s3, config.s3.bucketName.value)
    )
    libraryService = new LibraryServiceImpl(db, libraryDbio, bookImagesRepo)
    (pubsub, redis) <- RedisResource(config.redis.url, logger)
    credentialsRepo <- config.auth.session match {
      case AuthSessionConfig.RedisSession =>
        Resource.pure[Task, CredentialsRepo](
          new RedisCredentialsRepo(redis, config.auth.tokenTimeout)(key)
        )
      case AuthSessionConfig.InMemory =>
        InMemoryCredentialsRepo(config.auth.tokenTimeout, 10.seconds)(logger)
    }
    messageSubject <- RedisSubject(pubsub, data.RedisChannel("message"))
    authService = new AuthServiceImpl(
      credentialsRepo,
      usersRepo,
      config.auth.tokenTimeout
    )(key)
    apiRoutes = Metrics(Dropwizard[Task](registry, "server"))(
      new MessageRoutes(messageSubject)(logger).routes <+>
        Timeout(config.http.timeout)(
          new LibraryRoutes(libraryService, authService)(logger).routes <+>
            new LibraryRoutes2(libraryService, authService)(logger).routes <+>
            new AccountRoutes(authService, userService)(logger).routes <+>
            new UserRoutes(userService, authService)(logger).routes
        )
    )
    httpRoutes = apiRoutes <+> metricsRoutes(registry)
  } yield httpRoutes

  def metricsRoutes(registry: MetricRegistry) = HttpRoutes.of[Task] {
    case req if req.method === Method.GET && req.uri === uri"/api/metrics" =>
      metricsResponse(registry)
  }

}

final class RedisSubject(
    val tx: Pipe[Task, StreamEvent, Unit],
    val rx: fs2.Stream[Task, StreamEvent],
    val channel: data.RedisChannel[String]
)

object RedisSubject {
  def apply(ps: RedisStreamEventPs, channel: data.RedisChannel[String])(implicit
      logger: Logger[Task]
  ) = {
    for {
      (tx, rx) <- Resource.make(
        Task.pure(ps.publish(channel) -> ps.subscribe(channel))
      )(_ => logger.debug("Unsubbing") >> ps.unsubscribe(channel).compile.drain)
    } yield new RedisSubject(tx, rx, channel)
  }
}
