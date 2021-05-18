package wow.doge.http4sdemo.profile

import be.venneborg.refined.RefinedMapping
import be.venneborg.refined.RefinedSupport
import slick.jdbc.PostgresProfile
import com.github.tminglei.slickpg._
import enumeratum.SlickEnumSupport
import com.github.tminglei.slickpg.str.PgStringSupport

trait ExtendedPgProfile
    extends PostgresProfile
    with RefinedMapping
    with RefinedSupport
    with PgCirceJsonSupport
    with array.PgArrayJdbcTypes
    with PgStringSupport
    with SlickEnumSupport {
  override val pgjson = "jsonb"

  override val api = new API
    with RefinedImplicits
    with JsonImplicits
    with PgStringImplicits

}

object ExtendedPgProfile extends ExtendedPgProfile
