package wow.doge.http4sdemo.endpoints
import java.time.LocalDateTime

import scala.util.Try

import endpoints4s.Validated
import endpoints4s.algebra.Urls
import endpoints4s.generic.JsonSchemas
import eu.timepit.refined.api._

trait RefinedJsonSchemas extends JsonSchemas {
  implicit def refinedJsonSchema[T, P, F[_, _]](implicit
      jsonSchema: JsonSchema[T],
      validate: Validate[T, P],
      ref: RefType[F]
  ): JsonSchema[F[T, P]] = jsonSchema.xmapPartial(v =>
    endpoints4s.Validated.fromEither(ref.refine(v).left.map(Seq(_)))
  )(value => ref.unwrap(value))

  implicit lazy val localDateTimeJsonSchema: JsonSchema[LocalDateTime] =
    stringJsonSchema(format = Some("date-time"))
      .xmapPartial { instantString =>
        Validated.fromTry(Try(LocalDateTime.parse(instantString)))
      }(instant => instant.toString)
      .withExample(LocalDateTime.now())
      .withDescription("ISO 8601 Date and time in UTC")
}

trait RefinedUrls extends Urls {
  implicit def refinedSegment[T, P, F[_, _]](implicit
      segment: Segment[T],
      validate: Validate[T, P],
      ref: RefType[F]
  ): Segment[F[T, P]] = segment.xmapPartial(v =>
    endpoints4s.Validated.fromEither(ref.refine(v).left.map(Seq(_)))
  )(value => ref.unwrap(value))

  implicit def refinedQueryParam[T, P, F[_, _]](implicit
      segment: QueryStringParam[T],
      validate: Validate[T, P],
      ref: RefType[F]
  ): QueryStringParam[F[T, P]] = segment.xmapPartial(v =>
    endpoints4s.Validated.fromEither(ref.refine(v).left.map(Seq(_)))
  )(value => ref.unwrap(value))
}
