package wow.doge.http4sdemo

import java.time.LocalDateTime

import munit.FunSuite
import wow.doge.http4sdemo.models.Book2
import wow.doge.http4sdemo.slickcodegen.Tables

class WonkyTest extends FunSuite {
  test("blah blah") {

    Book2
      .fromBooksRow(
        Tables.BooksRow(-1, "Asfwasaqw", "awqwfqas", 12, LocalDateTime.now)
      )
      .toEither
      .left
      .foreach(_.iterator.foreach(println))

  }
}
