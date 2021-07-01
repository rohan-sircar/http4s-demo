package wow.doge.http4sdemo.server.utils

import _root_.emil._
import cats.data.Chain
import cats.data.NonEmptyList
import cats.effect.concurrent.Ref
import io.chrisdavenport.fuuid.FUUID
import io.odin.Logger
import jakarta.mail.MessagingException
import jakarta.mail.SendFailedException
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.implicits._

trait MailClient {

  def smtpConfig: MailConfig

  def send(
      mail: Mail[Task],
      mails: Mail[Task]*
  ): IO[AppError.MailClientError, NonEmptyList[String]]
}

object MailClient {

  def infoLine(mh: MailHeader): String =
    s"${mh.subject}:${mh.from.map(_.address).getOrElse("<no-from>")}->${mh.recipients.to
      .map(_.address)}"

  val dummyConfig = MailConfig.gmailSmtp("foo", "bar")
}

final class MailClientImpl(jmail: Emil[Task], val smtpConfig: MailConfig)
    extends MailClient {

  def send(
      mail: Mail[Task],
      mails: Mail[Task]*
  ): IO[AppError.MailClientError, NonEmptyList[String]] =
    jmail(smtpConfig).send(mail, mails: _*).mapErrorPartial {
      case ex: SendFailedException =>
        AppError.MailClientError(AppError.SendFailedError(ex.getMessage))
      case ex: MessagingException =>
        AppError.MailClientError(AppError.CouldNotConnectError(ex.getMessage))
    }

}

final class ConsoleLoggingMailClient(implicit logger: Logger[Task])
    extends MailClient {
  import _root_.emil.javamail.syntax._

  val smtpConfig = MailClient.dummyConfig

  def send(
      mail: Mail[Task],
      mails: Mail[Task]*
  ): IO[AppError.MailClientError, NonEmptyList[String]] =
    logger.debugU(s"Sending ${mails.size + 1} mail(s) ") *>
      NonEmptyList
        .of(mail, mails: _*)
        .traverse { mail =>
          for {
            msgId <- FUUID.randomFUUID[Task].hideErrors
            _ <- logger.debugU(
              s"Sending message: ${MailClient.infoLine(mail.header)}, $msgId"
            )
            mailString <- mail.serialize
            _ <- logger.infoU(s"Mail = $mailString")
          } yield msgId.toString
        }
        .hideErrors

}

final class SpyMailClient(
    val smtpConfig: MailConfig,
    ref: Ref[Task, Chain[(FUUID, Mail[Task])]],
    logger: Logger[Task]
) extends MailClient {
  def send(
      mail: Mail[Task],
      mails: Mail[Task]*
  ): IO[AppError.MailClientError, NonEmptyList[String]] =
    logger.debugU(s"Sending ${mails.size + 1} mail(s) ") *>
      NonEmptyList
        .of(mail, mails: _*)
        .traverse { mail =>
          for {
            msgId <- FUUID.randomFUUID[Task].hideErrors
            _ <- logger.debugU(
              s"Sending message: ${MailClient.infoLine(mail.header)}, $msgId"
            )
            _ <- ref.update(_ :+ (msgId -> mail)).hideErrors
          } yield msgId.toString
        }
        .hideErrors
}
