package wow.doge.http4sdemo.server.utils.emilutils

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import emil._
import io.odin

final class ConsoleLoggingSend[F[_]: Sync](logger: odin.Logger[F])
    extends Send[F, Connection] {
  def sendMails(
      mails: NonEmptyList[Mail[F]]
  ): MailOp[F, Connection, NonEmptyList[String]] =
    ConsoleLoggingSend.impl(mails, logger)
}

object ConsoleLoggingSend {

  def impl[F[_]](
      mails: NonEmptyList[Mail[F]],
      logger: odin.Logger[F]
  )(implicit F: Sync[F]): MailOp[F, Connection, NonEmptyList[String]] =
    MailOp(conn =>
      logger.debug(s"Sending ${mails.size} mail(s) using ${conn.config}") *>
        mails.traverse { mail =>
          for {
            msgId <- F.delay(UUID.randomUUID())
            _ <- logger.debug(
              s"Sending message: ${infoLine(mail.header)}, $msgId"
            )
          } yield msgId.toString
        }
    )

}
