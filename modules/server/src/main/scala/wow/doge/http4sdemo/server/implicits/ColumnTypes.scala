package wow.doge.http4sdemo.server.implicits

import io.estatico.newtype.ops._
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.models.common._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.refinements._
import wow.doge.http4sdemo.server.ExtendedPgProfile
import wow.doge.http4sdemo.server.ExtendedPgProfile.api._
import wow.doge.http4sdemo.server.ExtendedPgProfile.mapping._

trait ColumnTypes {
  implicit val bookIdColType: ExtendedPgProfile.ColumnType[BookId] =
    implicitly[ExtendedPgProfile.ColumnType[IdRefinement]]
      .coerce[ExtendedPgProfile.ColumnType[BookId]]

  implicit val authorIdColType: ExtendedPgProfile.ColumnType[AuthorId] =
    implicitly[ExtendedPgProfile.ColumnType[IdRefinement]]
      .coerce[ExtendedPgProfile.ColumnType[AuthorId]]

  implicit val bookTitleColType: ExtendedPgProfile.ColumnType[BookTitle] =
    implicitly[ExtendedPgProfile.ColumnType[StringRefinement]]
      .coerce[ExtendedPgProfile.ColumnType[BookTitle]]

  implicit val bookIsbnColType: ExtendedPgProfile.ColumnType[BookIsbn] =
    implicitly[ExtendedPgProfile.ColumnType[StringRefinement]]
      .coerce[ExtendedPgProfile.ColumnType[BookIsbn]]

  implicit val authorNameColType: ExtendedPgProfile.ColumnType[AuthorName] =
    implicitly[ExtendedPgProfile.ColumnType[StringRefinement]]
      .coerce[ExtendedPgProfile.ColumnType[AuthorName]]

  implicit val colorColType =
    ExtendedPgProfile.mappedColumnTypeForEnum("Color", Color)

  implicit val UserIdColType: ExtendedPgProfile.ColumnType[UserId] =
    implicitly[ExtendedPgProfile.ColumnType[IdRefinement]]
      .coerce[ExtendedPgProfile.ColumnType[UserId]]

  implicit val userRoleColType =
    ExtendedPgProfile.mappedColumnTypeForValueEnumForInt("UserRole", UserRole)

  implicit val UsernameColType: ExtendedPgProfile.ColumnType[Username] =
    implicitly[ExtendedPgProfile.ColumnType[UsernameRefinement]]
      .coerce[ExtendedPgProfile.ColumnType[Username]]

  implicit val UserPasswordColType: ExtendedPgProfile.ColumnType[UserPassword] =
    implicitly[ExtendedPgProfile.ColumnType[StringRefinement]]
      .coerce[ExtendedPgProfile.ColumnType[UserPassword]]

}

object ColumnTypes extends ColumnTypes
