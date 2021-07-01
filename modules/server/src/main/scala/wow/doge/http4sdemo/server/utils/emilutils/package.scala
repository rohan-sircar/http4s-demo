package wow.doge.http4sdemo.server.utils

import _root_.emil.Connection
import _root_.emil.MailConfig
import _root_.emil.MailHeader

package object emilutils {
  def infoLine(mh: MailHeader): String =
    s"${mh.subject}:${mh.from.map(_.address).getOrElse("<no-from>")}->${mh.recipients.to
      .map(_.address)}"

  val dummyConfig = MailConfig.gmailSmtp("foo", "bar")

  val dummyConnection: Connection = new Connection {
    val config = dummyConfig
  }
}
