package wow.doge.http4sdemo

import cats.data.ValidatedNec
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.boolean._
import eu.timepit.refined.collection._
import eu.timepit.refined.numeric._
import eu.timepit.refined.string._
import eu.timepit.refined.types.string.NonEmptyFiniteString
import shapeless.::
import shapeless.HNil

//TODO add regex to these refinements
package object refinements {
  type RefinementValidation[+A] = ValidatedNec[String, A]
  type IdRefinement = Int Refined Positive
  object IdRefinement extends RefinedTypeOps[IdRefinement, Int] {
    //for use in http router dsl, which takes a string as input
    def unapply(s: String): Option[IdRefinement] =
      s.toIntOption.flatMap(unapply)
  }

  type StringRefinement = String Refined Size[Interval.Closed[5, 50]]
  object StringRefinement extends RefinedTypeOps[StringRefinement, String]

  type UnhashedPasswordRefinement = String Refined Size[Interval.Closed[5, 200]]
  object UnhashedPasswordRefinement
      extends RefinedTypeOps[UnhashedPasswordRefinement, String]

  type HashedPasswordRefinement = NonEmptyFiniteString[1000]
  object HashedPasswordRefinement
      extends RefinedTypeOps[HashedPasswordRefinement, String]

  type PaginationRefinement = Int Refined Interval.Closed[0, 50]
  object PaginationRefinement extends RefinedTypeOps[PaginationRefinement, Int]

  type SearchQuery = NonEmptyFiniteString[25]

  type UsernameRefinement = String Refined Size[Interval.Closed[5, 50]]
  object UsernameRefinement extends RefinedTypeOps[UsernameRefinement, String]

  type UrlRefinement =
    String Refined AllOf[
      Size[Interval.Closed[1, 50]] :: StartsWith["http://"] :: HNil
    ]
  object UrlRefinement extends RefinedTypeOps[UrlRefinement, String]

  type EmailRefinement =
    String Refined AllOf[
      Size[Interval.Closed[1, 50]] ::
        MatchesRegex[
          """^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""
        ] ::
        HNil
    ]
  object EmailRefinement extends RefinedTypeOps[EmailRefinement, String]

}
