package wow.doge.http4sdemo.profile

import scala.reflect.ClassTag

import be.venneborg.refined.RefinedMapping
import be.venneborg.refined.RefinedSupport
import com.github.tminglei.slickpg.ExPostgresProfile
import com.github.tminglei.slickpg.PgDate2Support
import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.str.PgStringSupport
import enumeratum._
import slick.basic.Capability
import slick.jdbc
import slick.jdbc.PostgresProfile

trait MyPgCirceJsonSupport extends PgCirceJsonSupport {
  driver: PostgresProfile =>
  import io.circe._
  import io.circe.parser._
  import io.circe.syntax._

  trait MyJsonImplicits extends MyCirceImplicits

  trait MyCirceImplicits extends CirceImplicits {
    import utils.JsonUtils.clean
    override implicit val circeJsonTypeMapper: jdbc.JdbcType[Json] = {
      new GenericJdbcType[Json](
        pgjson,
        (v) => parse(v).getOrElse(Json.Null),
        //originally uses printer.spaces2
        //which causes peristed json to have (escaped) whitespace
        //so we override the type mapper with noSpaces
        (v) => clean(v.asJson.noSpaces),
        hasLiteralForm = false
      )
    }
  }

}

trait ExtendedPgProfile
    extends ExPostgresProfile
    with RefinedMapping
    with RefinedSupport
    with MyPgCirceJsonSupport
    with array.PgArrayJdbcTypes
    with PgStringSupport
    with PgSearchSupport
    with PgDate2Support
    with PgEnumSupport {
  override val pgjson = "jsonb"

  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate

  override val api = new API
    with RefinedImplicits
    with MyJsonImplicits
    with PgStringImplicits
    with SearchImplicits
    with SearchAssistants
    with DateTimeImplicits

  def mappedColumnTypeForEnum[E <: EnumEntry: ClassTag](
      sqlEnumTypeName: String,
      E: Enum[E]
  ): profile.BaseColumnType[E] =
    createEnumJdbcType[E](
      sqlEnumTypeName,
      _.entryName,
      s => E.withName(s),
      false
    )
}

object ExtendedPgProfile extends ExtendedPgProfile
