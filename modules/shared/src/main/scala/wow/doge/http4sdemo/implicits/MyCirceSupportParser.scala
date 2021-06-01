package wow.doge.http4sdemo.implicits

import io.circe.jawn.CirceSupportParser

trait MyCirceSupportParser {
  val circeSupportParser =
    new CirceSupportParser(maxValueSize = None, allowDuplicateKeys = false)
  implicit val circeSupportParserFacade = circeSupportParser.facade
}
