package wow.doge.http4sdemo.server.routes

import io.circe.Json
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.reactive.Observable
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.NewExtra
import wow.doge.http4sdemo.server.services.AuthService
import wow.doge.http4sdemo.server.services.LibraryService
import wow.doge.http4sdemo.server.utils.enrichLogger

final class LibraryRoutes(
    libraryService: LibraryService,
    authService: AuthService
)(logger: Logger[Task]) {

  val routes: HttpRoutes[Task] = {
    val dsl = Http4sDsl[Task]
    import dsl._
    object StringSearchQuery extends QueryParamDecoderMatcher[String]("q")
    HttpRoutes.of[Task] {

      case req @ GET -> Root / "api" / "extras" / "search" :?
          StringSearchQuery(q) =>
        import wow.doge.http4sdemo.server.utils.observableArrayJsonEncoder
        import io.circe.syntax._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Search book"))
        IO.deferAction(implicit s =>
          for {
            _ <- clogger.infoU("Request for searching extras")
            extras = libraryService.searchExtras(q)
            res <- Ok(extras.map(_.asJson))
          } yield res
        )

      case req @ PUT -> Root / "api" / "extras" =>
        import org.http4s.circe.CirceEntityCodec._
        implicit val clogger =
          enrichLogger(logger, req, Map("name" -> "Search book"))
        for {
          newExtra <- req.as[NewExtra]
          _ <- clogger.infoU("Request for creating extras")
          res <- libraryService
            .createExtra(newExtra)
            .flatMap(id => Created(id).hideErrors)
          // .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ GET -> Root / "api" / "subscribe" =>
        import org.http4s.circe.CirceEntityCodec._
        import io.circe.syntax._
        import scala.concurrent.duration._
        Observable
          .interval(2.seconds)
          .map(item => Json.obj("foo" := "bar", "counter" := item))
          .dump("O")
          .toStreamIO
          .flatMap(o => Ok(o))

      case req @ POST -> Root / "api" / "publish" =>
        // IO.deferAction(implicit s =>
        for {
          stream <- IO.pure(req.body.chunks.through(fs2.text.utf8DecodeC))
          _ <- stream.toObsU.flatMap(_.dump("O").completedL.toIO)
          res <- Ok()
        } yield res
      // )
    }
  }

}
