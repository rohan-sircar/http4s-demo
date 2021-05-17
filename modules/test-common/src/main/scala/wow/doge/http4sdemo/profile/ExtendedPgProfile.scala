package wow.doge.http4sdemo.profile

import be.venneborg.refined.RefinedMapping
import be.venneborg.refined.RefinedSupport
import slick.jdbc.PostgresProfile

trait ExtendedPgProfile
    extends PostgresProfile
    with RefinedMapping
    with RefinedSupport {

  override val api = new API with RefinedImplicits

}

object ExtendedPgProfile extends ExtendedPgProfile
