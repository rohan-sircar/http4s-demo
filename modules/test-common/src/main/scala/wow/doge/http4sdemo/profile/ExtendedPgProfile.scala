package wow.doge.http4sdemo.profile

import be.venneborg.refined.RefinedMapping
import be.venneborg.refined.RefinedSupport
import slick.jdbc.PostgresProfile
import com.github.tminglei.slickpg._
import enumeratum.SlickEnumSupport
import com.github.tminglei.slickpg.str.PgStringSupport
import com.github.tminglei.slickpg.PgDate2Support
// import com.github.tminglei.slickpg.d
import com.github.tminglei.slickpg.ExPostgresProfile
import slick.basic.Capability
import slick.driver

trait ExtendedPgProfile
    extends ExPostgresProfile
    with RefinedMapping
    with RefinedSupport
    with PgCirceJsonSupport
    with array.PgArrayJdbcTypes
    with PgStringSupport
    with SlickEnumSupport
    with PgSearchSupport
    with PgDate2Support {
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
}

object ExtendedPgProfile extends ExtendedPgProfile
