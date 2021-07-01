package wow.doge.http4sdemo.server.repos

import cats.effect.concurrent.Ref
import io.chrisdavenport.fuuid.FUUID
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import slick.jdbc.JdbcBackend
import wow.doge.http4sdemo.models.User
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.ExtendedPgProfile.api._
import wow.doge.http4sdemo.server.ExtendedPgProfile.mapping._
import wow.doge.http4sdemo.server.implicits._
import wow.doge.http4sdemo.slickcodegen.Tables

trait AccountActivationTokensRepo {
  def put(token: FUUID, user: User): UIO[Unit]
  def get(token: FUUID): UIO[Option[User]]
  def remove(token: FUUID): UIO[Unit]
}

final class AccountActivationTokensRepoImpl(
    db: JdbcBackend.DatabaseDef,
    dbio: AccountActivationTokensDbio
) extends AccountActivationTokensRepo {

  def put(token: FUUID, user: User): UIO[Unit] =
    db.runL(dbio.insertToken(token, user.id)).void.hideErrors

  def get(token: FUUID): UIO[Option[User]] =
    db.runL(dbio.getUserForToken(token)).hideErrors

  def remove(token: FUUID): UIO[Unit] =
    db.runL(dbio.deleteToken(token)).void.hideErrors
}

final class AccountActivationTokensDbio {
  def insertToken(token: FUUID, userId: UserId) =
    Tables.AccountActivationTokens += Tables.AccountActivationTokensRow(
      token.toString,
      userId
    )

  def getUserForToken(token: FUUID) = {
    val query = for {
      tokens <- Tables.AccountActivationTokens.filter(
        _.activeToken === token.toString
      )
      users <- Tables.Users
        .filter(
          _.userId === tokens.userId
        )
        .map(User.fromUsersTableFn)
    } yield users
    query.result.headOption
  }

  def deleteToken(token: FUUID) = {
    Tables.AccountActivationTokens
      .filter(_.activeToken === token.toString)
      .delete
  }
}

final class InMemoryAccountActivationTokensRepo(
    ref: Ref[Task, Map[FUUID, User]]
) extends AccountActivationTokensRepo {

  def put(token: FUUID, user: User): UIO[Unit] =
    ref.update(_ + (token -> user)).hideErrors

  def get(token: FUUID): UIO[Option[User]] =
    ref.get.map(_.get(token)).hideErrors

  def remove(token: FUUID): UIO[Unit] =
    ref.update(_ - token).hideErrors
}

object InMemoryAccountActivationTokensRepo {
  def apply() = for {
    ref <- Ref[Task].of(Map.empty[FUUID, User]).hideErrors
    repo = new InMemoryAccountActivationTokensRepo(ref)
  } yield repo
}

class NoopAccountActivationTokensRepo extends AccountActivationTokensRepo {

  def put(token: FUUID, user: User): UIO[Unit] =
    IO.terminate(new NotImplementedError)

  def get(token: FUUID): UIO[Option[User]] =
    IO.terminate(new NotImplementedError)

  def remove(token: FUUID): UIO[Unit] = IO.terminate(new NotImplementedError)

}
