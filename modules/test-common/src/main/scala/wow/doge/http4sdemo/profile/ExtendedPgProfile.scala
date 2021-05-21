package wow.doge.http4sdemo.profile

import be.venneborg.refined.RefinedMapping
import be.venneborg.refined.RefinedSupport
import slick.jdbc.PostgresProfile
import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.str.PgStringSupport
import com.github.tminglei.slickpg.PgDate2Support
// import com.github.tminglei.slickpg.d
import com.github.tminglei.slickpg.ExPostgresProfile
import slick.basic.Capability
import slick.driver
import slick.jdbc
import enumeratum._
import scala.reflect.ClassTag

trait MyPgCirceJsonSupport extends PgCirceJsonSupport {
  driver: PostgresProfile =>
  import driver.api._
  import io.circe._
  import io.circe.parser._
  import io.circe.syntax._

  trait JsonImplicits extends CirceImplicits2

  trait CirceImplicits2 extends CirceImplicits {
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
    super.computeCapabilities + driver.JdbcProfile.capabilities.insertOrUpdate

  override val api = new API
    with RefinedImplicits
    with JsonImplicits
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
