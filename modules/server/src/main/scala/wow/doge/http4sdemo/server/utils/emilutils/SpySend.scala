package wow.doge.http4sdemo.server.utils.emilutils

import java.util.UUID

import cats.data.Chain
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import emil._
import io.odin

final class SpySend[F[_]: Sync](ref: Ref[F, Chain[(UUID, Mail[F])]])(implicit
    logger: odin.Logger[F]
) extends Send[F, Connection] {
  def sendMails(
      mails: NonEmptyList[Mail[F]]
  ): MailOp[F, Connection, NonEmptyList[String]] = SpySend.impl(ref, mails)
}

object SpySend {

  def impl[F[_]](
      ref: Ref[F, Chain[(UUID, Mail[F])]],
      mails: NonEmptyList[Mail[F]]
  )(implicit
      F: Sync[F],
      logger: odin.Logger[F]
  ): MailOp[F, Connection, NonEmptyList[String]] =
    MailOp(conn =>
      logger.debug(s"Sending ${mails.size} mail(s) using ${conn.config}") *>
        mails.traverse { mail =>
          for {
            msgId <- F.delay(UUID.randomUUID())
            _ <- logger.debug(
              s"Sending message: ${infoLine(mail.header)}, $msgId"
            )
            _ <- ref.update(_ :+ (msgId -> mail))
          } yield msgId.toString
        }
    )

}
