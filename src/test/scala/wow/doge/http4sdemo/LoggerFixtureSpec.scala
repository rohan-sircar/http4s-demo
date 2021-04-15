package wow.doge.http4sdemo

// import sourcecode.File

class LoggerFixtureSpec extends MonixBioSuite {
  // "LoggerFixtureSpec"
  val fixture = loggerFixture()

  loggerFixture().test("blah blah") { logger =>
    logger.debug("blah blah blah")
  }
}
